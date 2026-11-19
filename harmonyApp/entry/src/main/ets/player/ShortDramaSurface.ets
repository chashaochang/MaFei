import { registerComposeInteropBuilder } from '@cpf-kmp-cmp/compose/src/main/ets/compose/ArkUIView';
import nativeApi from 'libentry.so';
import { createShortDramaPlayer, DramaPlayer, hasShortDramaAdvancedSource, hasShortDramaSdk } from './ShortDramaSdk';

interface DramaArgs {
  slotId: number;
  loadId: number;
  url: string;
  httpHeaders: string;
  allowInsecureTls: boolean;
  startPositionMs: number;
  playRevision: number;
  seekRevision: number;
  seekPositionMs: number;
  speed: number;
}

class DramaXComponent extends XComponentController {
  resize: (id: string, rect: SurfaceRect) => void = () => {};
  destroy: (id: string) => void = () => {};

  onSurfaceCreated(id: string): void { this.resize(id, this.getXComponentSurfaceRect()); }
  onSurfaceChanged(id: string, rect: SurfaceRect): void { this.resize(id, rect); }
  onSurfaceDestroyed(id: string): void { this.destroy(id); }
}

// ArkUI can apply the incoming page's args first. Revoke the previous owner's
// audio before starting the next one, independent of argument delivery order.
let playingSlot: ShortDramaSurface | undefined;
let latestPlayRevision = 0;

@Builder
function shortDramaBuilder(_args: Object): void {
  ShortDramaSurface();
}

export function registerShortDramaSurface(development: boolean): void {
  if (!development || !hasShortDramaSdk()) return;
  registerComposeInteropBuilder('MaFeiShortDramaSurface', shortDramaBuilder);
  nativeApi.UpdateOhosShortDramaState(JSON.stringify({
    available: true, advancedSource: hasShortDramaAdvancedSource()
  }));
}

@Component
struct ShortDramaSurface {
  @Consume('compose_args') @Watch('applyArgs') args: DramaArgs;
  private readonly surface: DramaXComponent = new DramaXComponent();
  private player?: DramaPlayer;
  private surfaceId: string = '';
  private widthPx: number = 0;
  private heightPx: number = 0;
  private slotId: number = 0;
  private opened: boolean = false;
  private loadId: number = 0;
  private seekRevision: number = 0;
  private speed: number = 1;
  private failed: boolean = false;
  private positionMs: number = 0;
  private firstFrame: boolean = false;
  private phase: string = '';
  private timer: number = -1;

  aboutToAppear(): void {
    this.slotId = this.args.slotId;
    this.player = createShortDramaPlayer();
    this.surface.resize = (id, rect): void => {
      if (rect.surfaceWidth < 1 || rect.surfaceHeight < 1) return;
      if (id !== this.surfaceId) this.close();
      this.surfaceId = id;
      this.widthPx = Math.round(rect.surfaceWidth);
      this.heightPx = Math.round(rect.surfaceHeight);
      if (this.opened) this.player?.resize(this.widthPx, this.heightPx);
      else this.applyArgs();
    };
    this.surface.destroy = (id): void => {
      if (id !== this.surfaceId) return;
      this.poll();
      this.close();
      this.surfaceId = '';
    };
    this.applyArgs();
    this.timer = setInterval(() => this.poll(), 100);
  }

  aboutToDisappear(): void {
    clearInterval(this.timer);
    this.close();
    this.player?.release();
    this.player = undefined;
  }

  private close(): void {
    if (playingSlot === this) playingSlot = undefined;
    if (this.opened) this.player?.stop();
    this.opened = false;
    this.phase = '';
    this.firstFrame = false;
    this.speed = 1;
  }

  private yieldPlayback(): void {
    if (this.opened && !this.player?.pause()) {
      this.close();
      this.failed = true;
      this.emit('error', 0, false);
    }
  }

  private applyArgs(): void {
    const player = this.player;
    if (!player || this.args.slotId !== this.slotId || this.slotId === 0) return;
    if (this.args.loadId !== this.loadId) {
      this.close();
      this.loadId = this.args.loadId;
      this.positionMs = this.args.startPositionMs;
      this.seekRevision = 0;
      this.failed = false;
    }
    if (this.failed || !this.args.url || !this.surfaceId) return;
    if (!this.opened) {
      this.opened = player.load(this.args.url, {
        id: this.surfaceId, width: this.widthPx, height: this.heightPx
      }, {
        startPositionMs: this.positionMs, playWhenReady: false,
        httpHeaders: JSON.parse(this.args.httpHeaders) as string[],
        allowInsecureTls: this.args.allowInsecureTls
      });
      if (!this.opened) {
        this.failed = true;
        this.emit('error', 0, false);
        return;
      }
    }
    if (this.args.speed !== this.speed && player.setPlaybackSpeed?.(this.args.speed)) {
      this.speed = this.args.speed;
    }
    if (this.args.seekRevision > this.seekRevision) {
      this.seekRevision = this.args.seekRevision;
      player.seekTo(this.args.seekPositionMs);
    }
    const revision = this.args.playRevision;
    if (revision > 0 && revision >= latestPlayRevision && playingSlot !== this) {
      playingSlot?.yieldPlayback();
      latestPlayRevision = revision;
      if (player.play()) {
        playingSlot = this;
      } else {
        playingSlot = undefined;
        this.close();
        this.failed = true;
        this.emit('error', 0, false);
      }
    } else if (revision === 0 && playingSlot === this) {
      this.yieldPlayback();
      playingSlot = undefined;
    }
  }

  private poll(): void {
    if (!this.opened) return;
    const snapshot = this.player?.snapshot();
    if (!snapshot) return;
    const phaseChanged = this.phase !== snapshot.phase;
    const frameChanged = this.firstFrame !== snapshot.firstFrame;
    this.positionMs = Math.max(0, Math.round(snapshot.positionMs));
    this.phase = snapshot.phase;
    this.firstFrame = snapshot.firstFrame;
    if (phaseChanged || frameChanged || this.phase === 'playing') {
      this.emit(this.phase, Math.max(0, Math.round(snapshot.durationMs)), this.firstFrame);
    }
    if (this.phase === 'error') {
      this.close();
      this.failed = true;
    }
  }

  private emit(phase: string, durationMs: number, firstFrame: boolean): void {
    nativeApi.UpdateOhosShortDramaState(JSON.stringify({
      slotId: this.slotId, loadId: this.loadId, phase: phase, firstFrame: firstFrame,
      positionMs: this.positionMs, durationMs: durationMs
    }));
  }

  build() {
    XComponent({
      id: `mafei-short-drama-${this.args.slotId}`,
      type: XComponentType.SURFACE,
      controller: this.surface
    }).width('100%').height('100%').backgroundColor(Color.Black);
  }
}
