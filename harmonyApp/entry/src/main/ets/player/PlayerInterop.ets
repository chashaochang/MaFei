import { common } from '@kit.AbilityKit';
import { registerComposeInteropBuilder } from '@cpf-kmp-cmp/compose/src/main/ets/compose/ArkUIView';
import {
  PlayerController,
  PlayerEvent,
  PlayerEventListener,
  PlayerEventType,
  PlayerOptions,
  PlayerSource,
  PlayerState,
  PlayerSurface,
  PlayerContentMode
} from 'mfplayer';

/** Parameters supplied by Compose's ArkUIView interop builder. */
interface MaFeiPlayerInteropArgs {
  commandId: number;
  handle: number;
  action: string;
  url: string;
  headers: string[];
  subtitleUrl: string;
  autoPlay: boolean;
  positionMs: number;
  speed: number;
  onState?: (state: MaFeiPlayerState) => void;
}

/** Snapshot sent from the native player surface back to Compose. */
interface MaFeiPlayerState {
  handle: number;
  itemStatus: number;
  timeControl: number;
  positionMs: number;
  durationMs: number;
  bufferedMs: number;
  errorKind: number;
}

@Builder
function mafeiPlayerInteropBuilder(args: Object): void {
  MaFeiPlayerInterop({ args: args as MaFeiPlayerInteropArgs });
}

/**
 * Thin ArkTS host for the legacy mfplayer SDK. It owns only the native video
 * surface and engine; Compose remains responsible for all visible controls.
 */
@ComponentV2
struct MaFeiPlayerInterop {
  @Param
  @Require
  readonly args: MaFeiPlayerInteropArgs;

  private readonly controller: PlayerController = new PlayerController();
  private appliedCommandId: number = -1;
  private loadedUrl: string = '';
  private readonly listener: PlayerEventListener = {
    onPlayerEvent: (event: PlayerEvent): void => this.reportState(event)
  };

  aboutToAppear(): void {
    const context = getContext(this) as common.UIAbilityContext;
    this.controller.setCacheDir(context.cacheDir);
    this.controller.addListener(this.listener);
    this.applyCommand();
  }

  aboutToDisappear(): void {
    this.controller.removeListener(this.listener);
    this.controller.release();
  }

  @Monitor('args')
  onArgsChanged(): void {
    this.applyCommand();
  }

  private applyCommand(): void {
    const command = this.args;
    if (command.commandId <= this.appliedCommandId) {
      return;
    }
    this.appliedCommandId = command.commandId;
    switch (command.action) {
      case 'load':
        this.load(command);
        break;
      case 'play':
        this.controller.play();
        break;
      case 'pause':
        this.controller.pause();
        break;
      case 'stop':
        this.controller.stop();
        break;
      case 'seek':
        this.controller.seekTo(Math.max(0, command.positionMs), false);
        break;
      case 'speed':
        if (command.speed > 0) {
          this.controller.setPlaybackSpeed(command.speed);
        }
        break;
      case 'release':
        this.controller.release();
        break;
      default:
        break;
    }
  }

  private load(command: MaFeiPlayerInteropArgs): void {
    const url = command.url.trim();
    if (url.length === 0 || url === this.loadedUrl) {
      return;
    }
    this.loadedUrl = url;
    const source: PlayerSource = {
      uri: url,
      headers: command.headers,
      externalSubtitles: command.subtitleUrl.trim().length > 0 ? [{
        uri: command.subtitleUrl.trim(),
        selected: true
      }] : []
    };
    const options: PlayerOptions = {
      useHardwareDecode: true,
      preferSurface: true,
      autoPlay: command.autoPlay,
      startPositionMs: Math.max(0, command.positionMs),
      playbackSpeed: command.speed > 0 ? command.speed : 1,
      progressIntervalMs: 250,
      exactSeek: false
    };
    this.controller.load(source, options);
  }

  private reportState(event: PlayerEvent): void {
    const callback = this.args.onState;
    if (!callback) {
      return;
    }
    const itemStatus = event.state === PlayerState.ERROR ? 2 :
      (event.state === PlayerState.IDLE || event.state === PlayerState.RELEASED ? 0 : 1);
    const timeControl = event.state === PlayerState.PLAYING ? 2 :
      (event.state === PlayerState.BUFFERING ? 1 : 0);
    callback({
      handle: this.args.handle,
      itemStatus: itemStatus,
      timeControl: timeControl,
      positionMs: Math.max(0, event.positionMs),
      durationMs: Math.max(0, event.durationMs),
      bufferedMs: Math.max(0, event.bufferedPositionMs),
      errorKind: event.type === PlayerEventType.ERROR ? 1 : 0
    });
  }

  build(): void {
    Stack() {
      PlayerSurface({
        controller: this.controller,
        contentMode: PlayerContentMode.Fit,
        onVideoSurfaceControllerReady: (_controller: XComponentController): void => {}
      })
        .width('100%')
        .height('100%')
    }
      .width('100%')
      .height('100%')
  }
}

export function registerMaFeiPlayerInteropBuilder(): void {
  registerComposeInteropBuilder('MaFeiPlayerSurface', mafeiPlayerInteropBuilder);
}
