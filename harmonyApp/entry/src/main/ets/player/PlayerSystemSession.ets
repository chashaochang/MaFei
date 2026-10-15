import { common } from '@kit.AbilityKit';
import { avSession } from '@kit.AVSessionKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { image } from '@kit.ImageKit';
import { util } from '@kit.ArkTS';
import { PlayerCasting, CastSource, CastEvent } from './PlayerCasting';

export interface SystemPlaybackMetadata {
  id: string;
  title: string;
  canNext: boolean;
  canPrevious: boolean;
  audioOnly?: boolean;
  castUrl?: string;
  /** Authenticated encoded image from the shared artwork loader; never a token-bearing URL. */
  artworkData?: string;
}

export interface SystemPlaybackSnapshot {
  state: string;
  positionMs: number;
  durationMs: number;
  bufferedMs: number;
  speed: number;
}

export type SystemPlaybackAction = (action: string, value: number) => void;

interface PendingSystemUpdate {
  metadata: SystemPlaybackMetadata;
  snapshot: SystemPlaybackSnapshot;
  timeMs: number;
}
interface SystemUpdateSlot {
  generation: number;
  pending: PendingSystemUpdate | undefined;
  scheduled: boolean;
}

/** Serialized platform session lifecycle. It owns no resource URL, credentials or playback queue. */
export class PlayerSystemSession {
  readonly casting: PlayerCasting = new PlayerCasting();
  private onCast: ((event: CastEvent) => void) | undefined = undefined;
  constructor(onCast?: (event: CastEvent) => void) { this.onCast = onCast; }
  private generation: number = 0;
  private session: avSession.AVSession | undefined = undefined;
  private operations: Promise<void> = Promise.resolve();
  private lastUpdateMs: number = 0;
  private lastState: string = '';
  private durationMs: number = -1;
  private updateSlot: SystemUpdateSlot | undefined = undefined;
  private mediaId: string = '';
  private mediaImage: image.PixelMap | undefined = undefined;
  private artworkData: string = '';
  private requestedArtwork: string = '';

  start(context: common.UIAbilityContext, metadata: SystemPlaybackMetadata,
    onAction: SystemPlaybackAction): void {
    const generation = ++this.generation;
    this.mediaId = metadata.id;
    this.requestedArtwork = metadata.artworkData ?? '';
    this.updateSlot = undefined;
    this.lastState = '';
    this.lastUpdateMs = 0;
    this.durationMs = -1;
    this.enqueue(async (): Promise<void> => {
      await this.destroyCurrent();
      if (generation !== this.generation) return;
      const session = await avSession.createAVSession(context, 'MaFei', metadata.audioOnly ? 'audio' : 'video');
      this.session = session;
      if (generation !== this.generation) {
        await this.destroyCurrent();
        return;
      }
      try {
        const emit = (action: string, value: number = 0): void => {
          if (generation === this.generation && this.session === session) onAction(action, value);
        };
        session.on('play', (): void => emit('play'));
        session.on('pause', (): void => emit('pause'));
        session.on('stop', (): void => emit('stop'));
        const castSource: CastSource | undefined = metadata.castUrl ? { id: metadata.id, title: metadata.title,
          audioOnly: metadata.audioOnly ?? false, mediaUri: metadata.castUrl, positionMs: 0, durationMs: 0 } : undefined;
        try {
          this.casting.bind(session, castSource, (event: CastEvent): void => {
            if (generation === this.generation) this.onCast?.(event);
          }, (action: string): void => emit(action));
        } catch (_error) {
          // Some devices reject outputDeviceChange even though the SDK declares it.
          // This optional integration must not destroy lock-screen / Bluetooth controls.
          hilog.warn(0x4D46, 'MaFeiPlayer', 'casting event registration unavailable');
        }
        session.on('seek', (position: number): void => {
          if (Number.isFinite(position) && position >= 0) emit('seek', position);
        });
        session.on('setSpeed', (speed: number): void => {
          if (Number.isFinite(speed) && speed > 0 && speed <= 4) emit('speed', speed);
        });
        if (metadata.canNext) session.on('playNext', (): void => emit('next'));
        if (metadata.canPrevious) session.on('playPrevious', (): void => emit('previous'));
        await this.writeMetadata(session, metadata, undefined, generation);
        if (generation !== this.generation) return;
        await session.activate();
      } catch (_error) {
        await this.destroyCurrent();
        throw new Error('system session initialization failed');
      }
    });
  }

  /** Artwork can arrive while paused, without a new playback/progress event. */
  updateMetadata(metadata: SystemPlaybackMetadata): void {
    if (metadata.id !== this.mediaId || (metadata.artworkData ?? '') === this.requestedArtwork) return;
    this.requestedArtwork = metadata.artworkData ?? '';
    const generation = this.generation;
    this.enqueue(async (): Promise<void> => {
      const session = this.session;
      if (!session || generation !== this.generation) return;
      await this.writeMetadata(session, metadata, this.durationMs < 0 ? undefined : this.durationMs, generation);
    });
  }

  private async decodeArtwork(encoded: string): Promise<image.PixelMap | undefined> {
    if (!encoded) return undefined;
    if (encoded.length > 5592408) throw new Error('artwork exceeds transfer limit');
    const bytes = new util.Base64Helper().decodeSync(encoded);
    const source = image.createImageSource(bytes.buffer as ArrayBuffer);
    try {
      const info = await source.getImageInfo();
      const scale = Math.min(1, 480 / Math.max(info.size.width, info.size.height));
      const options: image.DecodingOptions = { desiredSize: {
        width: Math.max(1, Math.round(info.size.width * scale)),
        height: Math.max(1, Math.round(info.size.height * scale)) }, editable: false };
      return await source.createPixelMap(options);
    } finally {
      await source.release();
    }
  }

  private async writeMetadata(session: avSession.AVSession, metadata: SystemPlaybackMetadata,
    duration: number | undefined, generation: number): Promise<void> {
    if (generation !== this.generation || session !== this.session) return;
    // Progress may carry an older metadata snapshot; only the artwork channel can change the image.
    const encoded = this.requestedArtwork;
    const previous = this.mediaImage;
    let artwork = previous;
    if (encoded !== this.artworkData) {
      try { artwork = await this.decodeArtwork(encoded); }
      catch (_error) { hilog.warn(0x4D46, 'MaFeiPlayer', 'system artwork decode failed'); }
    }
    if (generation !== this.generation || session !== this.session) {
      if (artwork && artwork !== previous) await artwork.release();
      return;
    }
    const data: avSession.AVMetadata = { assetId: metadata.id, title: metadata.title,
      duration: duration === undefined ? undefined : Math.max(0, duration), mediaImage: artwork };
    try { await session.setAVMetadata(data); }
    catch (_error) {
      if (artwork && artwork !== previous) await artwork.release();
      throw new Error('system metadata update failed');
    }
    if (generation !== this.generation || session !== this.session) {
      if (artwork && artwork !== previous) await artwork.release();
      return;
    }
    this.mediaImage = artwork;
    this.artworkData = encoded;
    this.casting.updateArtwork(artwork);
    if (previous && previous !== artwork) await previous.release();
  }

  update(metadata: SystemPlaybackMetadata, snapshot: SystemPlaybackSnapshot): void {
    if (metadata.id !== this.mediaId) return;
    this.casting.update(snapshot.positionMs, snapshot.durationMs);
    const now = Date.now();
    if (snapshot.state === this.lastState && now - this.lastUpdateMs < 1000) return;
    this.lastState = snapshot.state;
    this.lastUpdateMs = now;
    const generation = this.generation;
    let slot = this.updateSlot;
    if (!slot || slot.generation !== generation) {
      slot = { generation: generation, pending: undefined, scheduled: false };
      this.updateSlot = slot;
    }
    const pending: PendingSystemUpdate = { metadata: metadata, snapshot: snapshot, timeMs: now };
    slot.pending = pending;
    if (slot.scheduled) return;
    slot.scheduled = true;
    const scheduledSlot = slot;
    this.enqueue(async (): Promise<void> => {
      try {
        // At most one in-flight and one latest update, even if the OS service is slow.
        while (this.updateSlot === scheduledSlot && scheduledSlot.pending !== undefined) {
          const update = scheduledSlot.pending;
          scheduledSlot.pending = undefined;
          const session = this.session;
          if (session === undefined || generation !== this.generation) return;
          const snapshot = update.snapshot;
          const metadata = update.metadata;
          if (snapshot.durationMs !== this.durationMs) {
            await this.writeMetadata(session, metadata, snapshot.durationMs, generation);
            if (generation !== this.generation) return;
            this.durationMs = snapshot.durationMs;
          }
          if (generation !== this.generation) return;
          const state: avSession.AVPlaybackState = {
            state: this.playbackState(snapshot.state),
            speed: snapshot.speed,
            position: { elapsedTime: Math.max(0, snapshot.positionMs), updateTime: update.timeMs },
            bufferedTime: Math.max(0, snapshot.bufferedMs)
          };
          await session.setAVPlaybackState(state);
        }
      } finally {
        scheduledSlot.scheduled = false;
      }
    });
  }

  close(): void {
    ++this.generation;
    this.updateSlot = undefined;
    this.enqueue(async (): Promise<void> => this.destroyCurrent());
  }

  private async destroyCurrent(): Promise<void> {
    const session = this.session;
    this.session = undefined;
    if (session === undefined) return;
    try {
      await this.casting.close();
      session.off('play');
      session.off('pause');
      session.off('stop');
      session.off('seek');
      session.off('setSpeed');
      session.off('playNext');
      session.off('playPrevious');
    } finally {
      try { await session.destroy(); }
      finally {
        const artwork = this.mediaImage;
        this.mediaImage = undefined;
        this.artworkData = '';
        if (artwork) await artwork.release();
      }
    }
  }

  private enqueue(operation: () => Promise<void>): void {
    this.operations = this.operations.then(operation).catch((): void => {
      // System controls are optional; a service failure must not stop local playback.
      hilog.warn(0x4D46, 'MaFeiPlayer', 'system session operation failed');
    });
  }

  private playbackState(state: string): avSession.PlaybackState {
    switch (state) {
      case 'playing': return avSession.PlaybackState.PLAYBACK_STATE_PLAY;
      case 'paused': return avSession.PlaybackState.PLAYBACK_STATE_PAUSE;
      case 'ended':
      case 'error':
      case 'stopped': return avSession.PlaybackState.PLAYBACK_STATE_STOP;
      default: return avSession.PlaybackState.PLAYBACK_STATE_PREPARE;
    }
  }
}
