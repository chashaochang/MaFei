import { common } from '@kit.AbilityKit';
import { avSession } from '@kit.AVSessionKit';
import { promptAction } from '@kit.ArkUI';
import { url } from '@kit.ArkTS';
import { image } from '@kit.ImageKit';

export interface CastSource {
  id: string;
  title: string;
  mediaUri: string;
  audioOnly: boolean;
  positionMs: number;
  durationMs: number;
  mediaImage?: image.PixelMap;
}

export interface CastEvent {
  event: string;
  active: boolean;
  positionMs: number;
  durationMs: number;
  state?: string;
}

/** One remote sink for the current shared player, not a second playlist/player state machine. */
export class PlayerCasting {
  private generation: number = 0;
  private session: avSession.AVSession | undefined = undefined;
  private controller: avSession.AVCastController | undefined = undefined;
  private source: CastSource | undefined = undefined;
  private onEvent: ((event: CastEvent) => void) | undefined = undefined;
  private onAction: ((action: string) => void) | undefined = undefined;
  private connected: boolean = false;
  private attempt: number = 0;
  private outputObserved: boolean = false;

  get active(): boolean { return this.connected; }

  bind(session: avSession.AVSession, source: CastSource | undefined,
    onEvent: (event: CastEvent) => void, onAction: (action: string) => void): void {
    this.session = session; this.source = source; this.onEvent = onEvent; this.onAction = onAction;
    const generation = ++this.generation;
    session.on('outputDeviceChange', (state: avSession.ConnectionState, output: avSession.OutputDeviceInfo): void => {
      if (generation !== this.generation) return;
      const device = output.devices[0];
      if (device?.castCategory === avSession.AVCastCategory.CATEGORY_REMOTE &&
        state === avSession.ConnectionState.STATE_CONNECTED) {
        void this.start(generation);
      } else if (this.connected) void this.stop();
    });
    this.outputObserved = true;
  }

  update(positionMs: number, durationMs: number): void {
    if (!this.source || this.connected) return;
    this.source.positionMs = positionMs; this.source.durationMs = durationMs;
  }

  updateArtwork(artwork: image.PixelMap | undefined): void {
    if (this.source) this.source.mediaImage = artwork;
  }

  async choose(context: common.UIAbilityContext): Promise<void> {
    if (this.connected) { await this.stop(); return; }
    if (!this.outputObserved || !this.source || !this.validRemoteUrl(this.source.mediaUri)) {
      promptAction.showToast({ message: '此播放源暂不支持投屏' }); return;
    }
    try {
      const options: avSession.AVCastPickerOptions = { sessionType: this.source.audioOnly ? 'audio' : 'video' };
      await new avSession.AVCastPickerHelper(context).select(options);
    } catch (_error) { promptAction.showToast({ message: '无法打开投屏设备列表' }); }
  }

  async command(action: string, value: number): Promise<void> {
    const controller = this.controller;
    if (!this.connected || !controller) return;
    let command: avSession.AVCastControlCommand;
    switch (action) {
      case 'play': command = { command: 'play' }; break;
      case 'pause': command = { command: 'pause' }; break;
      case 'seek': command = { command: 'seek', parameter: Math.max(0, value) }; break;
      case 'speed': command = { command: 'setSpeed', parameter: value }; break;
      default: return;
    }
    try { await controller.sendControlCommand(command); }
    catch (_error) { promptAction.showToast({ message: '投屏控制失败' }); }
  }

  async close(): Promise<void> {
    ++this.generation;
    ++this.attempt;
    const session = this.session; this.session = undefined;
    this.onEvent = undefined; this.onAction = undefined; this.connected = false;
    this.detachController();
    this.source = undefined;
    if (session) {
      if (this.outputObserved) session.off('outputDeviceChange');
      this.outputObserved = false;
      try { await session.stopCasting(); } catch (_error) { /* Already local/disconnected. */ }
    }
  }

  private async stop(): Promise<void> {
    ++this.attempt;
    const session = this.session;
    const wasConnected = this.connected;
    this.connected = false; this.detachController();
    if (wasConnected) this.emit('casting', false);
    try { await session?.stopCasting(); } catch (_error) { /* Disconnected by receiver. */ }
  }

  private async start(generation: number): Promise<void> {
    const session = this.session; const source = this.source;
    if (!session || !source || !this.validRemoteUrl(source.mediaUri) || this.connected) return;
    const attempt = ++this.attempt;
    try {
      const controller = await session.getAVCastController();
      if (generation !== this.generation || attempt !== this.attempt) return;
      this.controller = controller;
      controller.on('playbackStateChange', 'all', (state: avSession.AVPlaybackState): void => {
        if (generation !== this.generation || attempt !== this.attempt || !this.connected) return;
        source.positionMs = Math.max(0, state.position?.elapsedTime ?? source.positionMs);
        let phase: string = 'buffering';
        if (state.state === avSession.PlaybackState.PLAYBACK_STATE_PLAY) phase = 'playing';
        else if (state.state === avSession.PlaybackState.PLAYBACK_STATE_PAUSE) phase = 'paused';
        this.emit(phase, true);
      });
      const current = (): boolean => generation === this.generation && attempt === this.attempt &&
        this.controller === controller && this.connected;
      controller.on('endOfStream', (): void => { if (current()) this.emit('ended', true); });
      controller.on('playNext', (): void => { if (current()) this.onAction?.('next'); });
      controller.on('playPrevious', (): void => { if (current()) this.onAction?.('previous'); });
      const description: avSession.AVMediaDescription = { assetId: source.id, title: source.title,
        mediaImage: source.mediaImage,
        mediaUri: source.mediaUri, mediaType: source.audioOnly ? 'AUDIO' : 'VIDEO', appName: '马飞',
        startPosition: source.positionMs, duration: source.durationMs };
      const item: avSession.AVQueueItem = { itemId: 0, description: description };
      await controller.prepare(item);
      if (generation !== this.generation || attempt !== this.attempt) return;
      this.connected = true; this.emit('casting', true);
      await controller.start(item);
      if (generation !== this.generation || attempt !== this.attempt) return;
      const initialState = await controller.getAVPlaybackState();
      if (generation === this.generation && attempt === this.attempt && this.connected) {
        source.positionMs = Math.max(0, initialState.position?.elapsedTime ?? source.positionMs);
        if (initialState.state === avSession.PlaybackState.PLAYBACK_STATE_PLAY) this.emit('playing', true);
        else if (initialState.state === avSession.PlaybackState.PLAYBACK_STATE_PAUSE) this.emit('paused', true);
      }
      // Device callbacks may have fired during start; do not invent a playing acknowledgement.
    } catch (_error) {
      if (generation !== this.generation || attempt !== this.attempt) return;
      await this.stop(); promptAction.showToast({ message: '投屏播放失败' });
    }
  }

  private detachController(): void {
    const controller = this.controller; this.controller = undefined;
    if (!controller) return;
    controller.off('playbackStateChange'); controller.off('endOfStream');
    controller.off('playNext'); controller.off('playPrevious');
  }

  private emit(event: string, active: boolean): void {
    const source = this.source;
    this.onEvent?.({ event: event, active: active, positionMs: source?.positionMs ?? 0,
      durationMs: source?.durationMs ?? 0 });
  }

  private validRemoteUrl(value: string): boolean {
    try {
      const parsed = url.URL.parseURL(value);
      const host = parsed.hostname.toLowerCase();
      return (parsed.protocol === 'http:' || parsed.protocol === 'https:') &&
        host !== 'localhost' && host !== '::1' && host !== '[::1]' && !host.startsWith('127.');
    } catch (_error) { return false; }
  }
}
