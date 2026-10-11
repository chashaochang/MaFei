import { common } from '@kit.AbilityKit';
import { BusinessError, deviceInfo } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';
import { audio } from '@kit.AudioKit';
import { image } from '@kit.ImageKit';
import { window } from '@kit.ArkUI';
import { registerComposeInteropBuilder } from '@cpf-kmp-cmp/compose/src/main/ets/compose/ArkUIView';
import nativeApi from 'libentry.so';
import {
  PlayerController,
  PlayerErrorCode,
  PlayerEvent,
  PlayerEventListener,
  PlayerEventType,
  PlayerOptions,
  PlayerRenderMode,
  PlayerSource,
  PlayerState,
  PlayerSurface,
  PlayerContentMode,
  PlayerTrack,
  PlayerVideoInfo
} from 'mfplayer';
import { VideoFramePreviewHelper } from './VideoFramePreviewHelper';
import { PlayerSystemSession, SystemPlaybackMetadata, SystemPlaybackSnapshot } from './PlayerSystemSession';
import { SystemAvPlayerEngine, SystemEngineSnapshot, SystemEngineSource, SystemEngineTrack } from './SystemAvPlayerEngine';
import { PlayerPictureInPicture, PlayerPiPSnapshot } from './PlayerPictureInPicture';
import { PlayerBackgroundAudio } from './PlayerBackgroundAudio';
import { CastEvent } from './PlayerCasting';
import { PlayerVideoBackdropCapture } from './PlayerVideoBackdropCapture';

interface MaFeiPlayerSurfaceArgs extends SystemEngineSource {
  commandId: number;
  backdropRevision: number;
  backdropSampling: boolean;
  handle: number;
  action: string;
  mediaId: string;
  title: string;
  artworkData: string;
  canNext: boolean;
  canPrevious: boolean;
  url: string;
  headers: string[];
  subtitleUrl: string;
  allowInsecureTls: boolean;
  multiConnectionAllowed: boolean;
  audioOnly: boolean;
  positionMs: number;
  speed: number;
  trackId: number;
  contentMode: string;
  renderMode: string;
  useHardwareDecode: boolean;
  cacheSizeMb: number;
  engine: string;
  /** setBrightness 用 0..1；setVolume 用步数。 */
  level: number;
  previewRequestId: number;
}

const PLAYER_LOG_DOMAIN = 0x4D46;
const PLAYER_LOG_TAG = 'MaFeiPlayer';

interface MaFeiPlayerTrackState {
  id: number;
  kind: string;
  label: string;
  language?: string;
  selected: boolean;
}

interface MaFeiPlayerEventState {
  handle: number;
  event: string;
  positionMs: number;
  durationMs: number;
  bufferedMs: number;
  failure?: string;
  systemAction?: string;
  systemValue?: number;
  pipActive?: boolean;
  backgroundAudioActive?: boolean;
  castingActive?: boolean;
  engine?: string;
  tracks: MaFeiPlayerTrackState[];
  /** event = 'device' 专用：窗口亮度与媒体音量，不属于任何一次播放。 */
  brightness?: number;
  volume?: number;
  maxVolume?: number;
  previewRequestId?: number;
  previewData?: string;
  previewWidthPx?: number;
  previewHeightPx?: number;
  decodeMode?: string;
  decoder?: string;
  renderer?: string;
  videoCodec?: string;
  videoCodecProfile?: string;
  videoPixelFormat?: string;
  videoPrimaries?: string;
  videoTransfer?: string;
  outputPrimaries?: string;
  outputTransfer?: string;
  dynamicRange?: string;
  videoWidthPx?: number;
  videoHeightPx?: number;
  videoBitRate?: number;
  audioBitRate?: number;
  audioSampleRateHz?: number;
  audioChannels?: number;
  renderedDropCount?: number;
  decoderDropCount?: number;
  delayedFrameCount?: number;
  downloadSpeedBytesPerSecond?: number;
  bufferedDurationMs?: number;
  networkSource?: boolean;
  configuredConnections?: number;
  activeConnections?: number;
}

// CustomBuilderAdapter: cmp-arkuiview-player-host
@Builder
function mafeiPlayerSurfaceBuilder(_args: Object): void {
  MaFeiPlayerSurfaceHost();
}

/** Native video engine/surface only. CMP owns every visible control above it. */
@Component
struct MaFeiPlayerSurfaceHost {
  @Consume('compose_args') @Watch('onArgsChanged') args: MaFeiPlayerSurfaceArgs;

  /**
   * Picture fitting. The CMP layer owns the video AREA geometry (原始比例 and
   * the fixed ratios reshape the Compose node), so only the two scalings
   * mfplayer must apply are mirrored here: 填满 crops, everything else
   * letterboxes. @State because PlayerSurface must re-render on a change.
   */
  @State contentMode: PlayerContentMode = PlayerContentMode.Fit;

  private playerWindow: window.Window | undefined = undefined;
  private readonly controller: PlayerController = new PlayerController();
  private readonly previewHelper: VideoFramePreviewHelper = new VideoFramePreviewHelper();
  private readonly systemSession: PlayerSystemSession = new PlayerSystemSession(
    (event: CastEvent): void => this.castEvent(event));
  private readonly pictureInPicture: PlayerPictureInPicture = new PlayerPictureInPicture();
  private readonly backgroundAudio: PlayerBackgroundAudio = new PlayerBackgroundAudio();
  private readonly glassBackdrop: PlayerVideoBackdropCapture = new PlayerVideoBackdropCapture(
    (handle: number, revision: number, width: number, height: number, alpha: number, pixels: ArrayBuffer): boolean =>
      nativeApi.UpdateOhosPlayerBackdrop(handle, revision, width, height, alpha, pixels));
  private hostForeground: boolean = true;
  private mpvSurface: XComponentController | undefined = undefined;
  @State private usingSystem: boolean = false;
  private readonly systemSurface: XComponentController = new XComponentController();
  private readonly systemEngine: SystemAvPlayerEngine = new SystemAvPlayerEngine(
    (snapshot: SystemEngineSnapshot): void => this.systemEvent(snapshot),
    (positionMs: number, speed: number, playing: boolean): void => this.fallbackToMpv(positionMs, speed, playing));
  private lastLoad: MaFeiPlayerSurfaceArgs | undefined = undefined;
  private systemReady: boolean = false;
  private fallbackReady: boolean = false;
  private fallbackPosition: number = 0;
  private fallbackSpeed: number = 1;
  private fallbackPlaying: boolean = false;
  private selectedAudio: number = -1;
  private selectedSubtitle: number = -1;
  private lastSystemTracks: SystemEngineTrack[] = [];
  private fallbackTracks: boolean = false;
  private sessionMetadata: SystemPlaybackMetadata = { id: '', title: '', canNext: false, canPrevious: false };
  private sessionPhase: string = 'ready';
  private appliedCommandId: number = -1;
  private lastPlayerState: PlayerState = PlayerState.IDLE;
  private lastDiagnosticsAt: number = 0;
  private readonly listener: PlayerEventListener = {
    onPlayerEvent: (event: PlayerEvent): void => this.handlePlayerEvent(event)
  };
  private readonly onHostBackground = (): void => {
    this.hostForeground = false;
    this.glassBackdrop.stop();
  };
  private readonly onHostForeground = (): void => {
    this.hostForeground = true;
    // The shared reducer owns pause/resume intent. Returning through the app
    // icon must also return the original surface, not leave a black PiP page.
    this.pictureInPicture.restoreToPlayer();
    this.syncGlassBackdrop();
  };

  aboutToAppear(): void {
    hilog.info(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG, 'surface host appear command=%{public}d action=%{public}s',
      this.args.commandId, this.args.action);
    const context = getContext(this) as common.UIAbilityContext;
    this.controller.setCacheDir(context.cacheDir);
    this.controller.addListener(this.listener);
    context.eventHub.on('onBackground', this.onHostBackground);
    context.eventHub.on('onForeground', this.onHostForeground);
    this.applyCommand();
    // 亮度和音量按旧版一样只挂在播放页：进页面先报一次，CMP 侧的手势才有基准值。
    void this.emitDeviceState();
  }

  aboutToDisappear(): void {
    this.glassBackdrop.stop();
    const context = getContext(this) as common.UIAbilityContext;
    this.emitProgress(true);
    context.eventHub.off('onBackground', this.onHostBackground);
    context.eventHub.off('onForeground', this.onHostForeground);
    this.controller.removeListener(this.listener);
    this.systemEngine.release();
    this.pictureInPicture.close();
    this.backgroundAudio.close();
    this.systemSession.close();
    this.controller.release();
    void this.previewHelper.release();
  }

  onArgsChanged(): void {
    this.applyCommand();
    this.syncSystemArtwork();
    this.syncGlassBackdrop();
  }

  private syncSystemArtwork(): void {
    if (!this.lastLoad || this.lastLoad.handle !== this.args.handle || this.sessionMetadata.id !== this.args.mediaId) return;
    const encoded = this.args.artworkData || '';
    if (encoded === (this.sessionMetadata.artworkData ?? '')) return;
    this.sessionMetadata = { id: this.sessionMetadata.id, title: this.sessionMetadata.title,
      canNext: this.sessionMetadata.canNext, canPrevious: this.sessionMetadata.canPrevious,
      audioOnly: this.sessionMetadata.audioOnly, castUrl: this.sessionMetadata.castUrl, artworkData: encoded };
    this.systemSession.updateMetadata(this.sessionMetadata);
  }

  private syncGlassBackdrop(): void {
    const surface = this.usingSystem ? this.systemSurface : this.mpvSurface;
    const ready = this.usingSystem ? this.systemReady :
      [PlayerState.READY, PlayerState.PLAYING, PlayerState.PAUSED, PlayerState.BUFFERING, PlayerState.ENDED]
        .indexOf(this.controller.getState()) >= 0;
    const path = this.controller.getRenderPathInfo();
    const supported = this.usingSystem || (path.actualMode !== 'surface-hw' && path.actualMode !== 'unknown');
    this.glassBackdrop.bind(surface ? surface.getXComponentSurfaceId() : '', this.args.handle,
      this.args.backdropRevision, this.hostForeground && this.args.backdropSampling && ready && supported && !this.args.audioOnly);
  }

  private applyCommand(): void {
    const command = this.args;
    if (command.commandId <= this.appliedCommandId) {
      return;
    }
    this.appliedCommandId = command.commandId;
    hilog.info(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG, 'apply command=%{public}d action=%{public}s handle=%{public}d',
      command.commandId, command.action, command.handle);
    // Every command carries the current mode, so a surface that appears after
    // the viewer already picked a ratio still renders correctly.
    this.contentMode = command.contentMode === 'Fill' ? PlayerContentMode.Fill : PlayerContentMode.Fit;
    if (this.usingSystem) this.systemEngine.fit(command.contentMode === 'Fill');
    if (command.action === 'cast') {
      void this.systemSession.casting.choose(getContext(this) as common.UIAbilityContext); return;
    }
    if (this.systemSession.casting.active &&
      (command.action === 'play' || command.action === 'pause' || command.action === 'seek' || command.action === 'speed')) {
      void this.systemSession.casting.command(command.action, command.action === 'speed' ? command.speed : command.positionMs);
      return;
    }
    if (command.action === 'pip') {
      const surface = this.usingSystem ? this.systemSurface : this.mpvSurface;
      if (!surface) return;
      const handle = command.handle;
      this.pictureInPicture.start(getContext(this) as common.UIAbilityContext, surface,
        (active: boolean): void => {
          const payload: MaFeiPlayerEventState = { handle: handle, event: 'pip', pipActive: active,
            positionMs: 0, durationMs: 0, bufferedMs: 0, tracks: [] };
          nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
        }, (action: string): void => {
          const payload: MaFeiPlayerEventState = { handle: handle, event: 'systemAction', systemAction: action,
            positionMs: 0, durationMs: 0, bufferedMs: 0, tracks: [] };
          nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
        }, this.pictureInPictureSnapshot());
      return;
    }
    if (command.action === 'stop' || command.action === 'release') {
      this.pictureInPicture.close();
      this.backgroundAudio.close();
    }
    if (this.usingSystem && command.action !== 'load') {
      switch (command.action) {
        case 'play': this.systemEngine.play(command.positionMs); return;
        case 'pause': this.systemEngine.pause(); return;
        case 'seek': this.systemEngine.seek(command.positionMs); return;
        case 'speed': this.systemEngine.speed(command.speed); return;
        case 'selectAudio': this.selectedAudio = command.trackId; this.systemEngine.select('audio', command.trackId); return;
        case 'selectSubtitle': this.selectedSubtitle = command.trackId; this.systemEngine.select('subtitle', command.trackId); return;
        case 'stop':
        case 'release': this.systemEngine.release(); this.systemSession.close(); return;
      }
    }
    switch (command.action) {
      case 'load':
        this.load(command);
        break;
      case 'play':
        if (command.positionMs > 0) {
          this.controller.seekTo(command.positionMs, false);
        }
        this.controller.play();
        break;
      case 'pause':
        this.controller.pause();
        break;
      case 'stop':
        this.emitProgress(true);
        this.systemSession.close();
        this.controller.stop();
        break;
      case 'seek':
        this.controller.seekTo(Math.max(0, command.positionMs), false);
        break;
      case 'speed':
        if (command.speed > 0) this.controller.setPlaybackSpeed(command.speed);
        break;
      case 'selectAudio':
        this.selectedAudio = command.trackId;
        if (command.trackId >= 0) this.controller.selectAudioTrack(command.trackId);
        break;
      case 'selectSubtitle':
        this.selectedSubtitle = command.trackId;
        this.controller.selectSubtitleTrack(command.trackId);
        break;
      case 'release':
        this.systemSession.close();
        this.controller.release();
        break;
      // 亮度/音量（旧版左侧调亮度、右侧调音量）：只作用于播放页窗口与媒体音量流。
      case 'setBrightness':
        void this.applyBrightness(command.level);
        break;
      case 'setVolume':
        this.applyVolume(command.level);
        break;
      case 'deviceState':
        void this.emitDeviceState();
        break;
      case 'preview':
        void this.emitPreview(command);
        break;
      case 'renderMode':
        // The native engine applies the selected path when the next source is
        // loaded. Keeping the preference in the command state also makes it
        // survive a surface recreation without rebuilding the current video.
        break;
      default:
        break;
    }
  }

  /** 窗口实例只在第一次用的时候取：拖动亮度时每帧都要写，不能每帧都 getLastWindow。 */
  private async resolveWindow(): Promise<window.Window | undefined> {
    if (this.playerWindow !== undefined) {
      return this.playerWindow;
    }
    try {
      this.playerWindow = await window.getLastWindow(getContext(this) as common.UIAbilityContext);
    } catch (_error) {
      hilog.warn(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG, 'window unavailable for brightness');
    }
    return this.playerWindow;
  }

  private async applyBrightness(value: number): Promise<void> {
    const win = await this.resolveWindow();
    if (win === undefined) {
      return;
    }
    try {
      await win.setWindowBrightness(Math.max(0, Math.min(1, value)));
    } catch (_error) {
      hilog.warn(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG, 'setWindowBrightness rejected');
    }
  }

  private applyVolume(steps: number): void {
    try {
      audio.getAudioManager().setVolume(audio.AudioVolumeType.MEDIA, Math.max(0, Math.round(steps)),
        (): void => {
        });
    } catch (_error) {
      hilog.warn(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG, 'setVolume rejected');
    }
  }

  /**
   * 上报窗口亮度与媒体音量。任何一个读不到就报 -1 / 0，KMP 侧据此让对应手势不介入
   * （宁可不响应，也不画一条改了没效果的反馈条）。
   */
  private async emitDeviceState(): Promise<void> {
    let brightness = -1;
    let volume = -1;
    let maxVolume = 0;
    const win = await this.resolveWindow();
    if (win !== undefined) {
      try {
        brightness = win.getWindowProperties().brightness;
      } catch (_error) {
        brightness = -1;
      }
    }
    try {
      const groupManager = audio.getAudioManager().getVolumeManager()
        .getVolumeGroupManagerSync(audio.DEFAULT_VOLUME_GROUP_ID);
      maxVolume = groupManager.getMaxVolumeSync(audio.AudioVolumeType.MEDIA);
      volume = groupManager.getVolumeSync(audio.AudioVolumeType.MEDIA);
    } catch (_error) {
      volume = -1;
      maxVolume = 0;
    }
    const payload: MaFeiPlayerEventState = {
      handle: this.args.handle,
      event: 'device',
      positionMs: 0,
      durationMs: 0,
      bufferedMs: 0,
      tracks: [],
      brightness: brightness,
      volume: volume,
      maxVolume: maxVolume
    };
    nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
  }

  private async emitPreview(command: MaFeiPlayerSurfaceArgs): Promise<void> {
    const requestId = command.previewRequestId;
    if (requestId <= 0 || command.url.trim().length === 0) {
      return;
    }
    const pixelMap = await this.previewHelper.fetchFrameByTime(
      command.url,
      Math.max(0, command.positionMs),
      command.headers,
    );
    if (pixelMap === undefined) {
      this.emitPreviewResult(command.handle, requestId);
      return;
    }
    let packer: image.ImagePacker | undefined = undefined;
    try {
      const imageInfo = await pixelMap.getImageInfo();
      packer = image.createImagePacker();
      const packed = await packer.packing(pixelMap, {
        format: 'image/jpeg',
        quality: 82
      });
      this.emitPreviewResult(
        command.handle,
        requestId,
        this.encodeBase64(packed),
        imageInfo.size.width,
        imageInfo.size.height,
      );
    } catch (error) {
      hilog.warn(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG,
        'preview encode failed %{public}s', `${JSON.stringify(error as BusinessError)}`);
      this.emitPreviewResult(command.handle, requestId);
    } finally {
      if (packer !== undefined) {
        packer.release();
      }
      pixelMap.release();
    }
  }

  private emitPreviewResult(handle: number, requestId: number, data: string = '',
    width: number = 0, height: number = 0): void {
    const payload: MaFeiPlayerEventState = {
      handle: handle,
      event: 'preview',
      positionMs: 0,
      durationMs: 0,
      bufferedMs: 0,
      tracks: [],
      previewRequestId: requestId,
      previewData: data,
      previewWidthPx: width,
      previewHeightPx: height
    };
    nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
  }

  private encodeBase64(buffer: ArrayBuffer): string {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
    const bytes = new Uint8Array(buffer);
    let result: string = '';
    for (let index = 0; index < bytes.length; index += 3) {
      const first = bytes[index];
      const second = index + 1 < bytes.length ? bytes[index + 1] : 0;
      const third = index + 2 < bytes.length ? bytes[index + 2] : 0;
      const combined = (first << 16) | (second << 8) | third;
      result += alphabet.charAt((combined >> 18) & 63);
      result += alphabet.charAt((combined >> 12) & 63);
      result += index + 1 < bytes.length ? alphabet.charAt((combined >> 6) & 63) : '=';
      result += index + 2 < bytes.length ? alphabet.charAt(combined & 63) : '=';
    }
    return result;
  }

  private load(command: MaFeiPlayerSurfaceArgs): void {
    this.pictureInPicture.close();
    this.lastLoad = command;
    this.systemReady = false; this.fallbackReady = false;
    this.lastSystemTracks = []; this.fallbackTracks = false;
    this.selectedAudio = -1; this.selectedSubtitle = -1;
    this.systemEngine.release();
    this.usingSystem = command.engine === 'System';
    if (this.usingSystem) {
      this.controller.stop();
      this.startSystemSession(command);
      this.systemEngine.load(command);
      this.systemEngine.fit(command.contentMode === 'Fill');
    } else this.loadMpv(command, command.positionMs, command.speed);
  }

  private systemEvent(snapshot: SystemEngineSnapshot): void {
    if (!this.usingSystem || snapshot.handle !== this.args.handle) return;
    if (this.systemSession.casting.active) return;
    if (snapshot.event === 'ready') this.systemReady = true;
    this.syncGlassBackdrop();
    if (snapshot.event === 'tracks') this.lastSystemTracks = snapshot.tracks.slice();
    if (snapshot.event !== 'progress' && snapshot.event !== 'tracks') this.sessionPhase = snapshot.event;
    const state: SystemPlaybackSnapshot = { state: this.sessionPhase, positionMs: snapshot.positionMs,
      durationMs: snapshot.durationMs, bufferedMs: snapshot.bufferedMs, speed: this.args.speed };
    this.systemSession.update(this.sessionMetadata, state);
    this.backgroundAudio.update(this.args.audioOnly, this.sessionPhase);
    this.pictureInPicture.update(this.pictureInPictureSnapshot());
    nativeApi.UpdateOhosPlayerState(JSON.stringify(snapshot));
  }

  private pictureInPictureSnapshot(): PlayerPiPSnapshot {
    return { widthPx: this.usingSystem ? this.systemEngine.videoWidthPx : this.controller.getVideoWidth(),
      heightPx: this.usingSystem ? this.systemEngine.videoHeightPx : this.controller.getVideoHeight(),
      playing: this.sessionPhase === 'playing' || this.sessionPhase === 'buffering' };
  }

  private fallbackToMpv(positionMs: number, speed: number, playing: boolean): void {
    const source = this.lastLoad;
    if (!this.usingSystem || !source || source.handle !== this.args.handle) return;
    this.pictureInPicture.close();
    const pipState: MaFeiPlayerEventState = { handle: source.handle, event: 'pip', pipActive: false,
      positionMs: 0, durationMs: 0, bufferedMs: 0, tracks: [] };
    nativeApi.UpdateOhosPlayerState(JSON.stringify(pipState));
    this.fallbackReady = this.systemReady;
    this.fallbackTracks = this.systemReady;
    this.fallbackPosition = positionMs; this.fallbackSpeed = speed; this.fallbackPlaying = playing;
    this.usingSystem = false;
    const engineState: MaFeiPlayerEventState = { handle: source.handle, event: 'engine', engine: 'Mpv',
      positionMs: 0, durationMs: 0, bufferedMs: 0, tracks: [] };
    nativeApi.UpdateOhosPlayerState(JSON.stringify(engineState));
    // The same validated resource and generation survive runtime fallback.
    // No new provider request or duplicate progress-start report is needed.
    this.loadMpv(source, positionMs, speed);
  }

  private startSystemSession(command: MaFeiPlayerSurfaceArgs): void {
    this.sessionMetadata = { id: command.mediaId || `playback-${command.handle}`,
      title: command.title || '马飞', canNext: command.canNext, canPrevious: command.canPrevious,
      audioOnly: command.audioOnly, castUrl: command.headers.length === 0 ? command.url : undefined,
      artworkData: command.artworkData || '' };
    this.sessionPhase = 'ready';
    const handle = command.handle;
    this.backgroundAudio.bind(getContext(this) as common.UIAbilityContext, (active: boolean): void => {
      const payload: MaFeiPlayerEventState = { handle: handle, event: 'backgroundAudio',
        backgroundAudioActive: active, positionMs: 0, durationMs: 0, bufferedMs: 0, tracks: [] };
      nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
    });
    this.systemSession.start(getContext(this) as common.UIAbilityContext, this.sessionMetadata,
      (action: string, value: number): void => {
        const payload: MaFeiPlayerEventState = { handle: handle, event: 'systemAction', systemAction: action,
          systemValue: value, positionMs: 0, durationMs: 0, bufferedMs: 0, tracks: [] };
        nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
      });
  }

  private castEvent(event: CastEvent): void {
    if (event.event === 'casting') {
      if (event.active) {
        this.pictureInPicture.close();
        if (this.usingSystem) this.systemEngine.pause(); else this.controller.pause();
      }
      const payload: MaFeiPlayerEventState = { handle: this.args.handle, event: 'casting', castingActive: event.active,
        positionMs: event.positionMs, durationMs: event.durationMs, bufferedMs: 0, tracks: [] };
      nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
      if (!event.active) {
        if (this.usingSystem) {
          this.systemEngine.seek(event.positionMs);
          if (this.sessionPhase === 'playing' || this.sessionPhase === 'buffering') this.systemEngine.play(event.positionMs);
        } else {
          this.controller.seekTo(event.positionMs, false);
          if (this.sessionPhase === 'playing' || this.sessionPhase === 'buffering') this.controller.play();
        }
        this.backgroundAudio.update(this.args.audioOnly, this.sessionPhase);
      }
      return;
    }
    this.sessionPhase = event.event;
    const progress: MaFeiPlayerEventState = { handle: this.args.handle, event: 'progress', positionMs: event.positionMs,
      durationMs: event.durationMs, bufferedMs: 0, tracks: [] };
    nativeApi.UpdateOhosPlayerState(JSON.stringify(progress));
    progress.event = event.event;
    nativeApi.UpdateOhosPlayerState(JSON.stringify(progress));
    this.backgroundAudio.update(true, event.event);
  }

  private loadMpv(command: MaFeiPlayerSurfaceArgs, positionMs: number, speed: number): void {
    const url = command.url.trim();
    if (url.length === 0) return;
    const source: PlayerSource = {
      uri: url,
      headers: command.headers,
      allowInsecureTls: command.allowInsecureTls,
      externalSubtitles: command.subtitleUrl.trim().length > 0 ? [{
        uri: command.subtitleUrl.trim(),
        selected: true
      }] : []
    };
    const options: PlayerOptions = {
      useHardwareDecode: command.useHardwareDecode,
      cacheSizeMb: command.cacheSizeMb,
      // The HarmonyOS 2-in-1/PC host does not expose a usable Vulkan surface;
      // its buffer/gpu path crashes inside libmpv during pl_vulkan_create.
      // Keep the compatibility surface path for that host. Phones/tablets
      // follow the user's persisted Buffer/Direct preference.
      renderMode: deviceInfo.deviceType === '2in1' ?
        PlayerRenderMode.SURFACE :
        (command.renderMode === 'Direct' ? PlayerRenderMode.SURFACE : PlayerRenderMode.BUFFER),
      // The shared PlayerStateMachine sends Play/Seek only after READY.
      autoPlay: false,
      startPositionMs: Math.max(0, positionMs),
      playbackSpeed: speed > 0 ? speed : 1,
      // The native view consumes smooth progress locally. KMP receives only
      // throttled progress reports for provider bookkeeping.
      progressIntervalMs: 250,
      exactSeek: false,
      multiConnectionEnabled: command.multiConnectionAllowed,
      // The proxy publishes complete blocks, not partial downloads. Its
      // 12 x 2 MB default repeatedly exhausts the 30 s request timeout on
      // slower links before startup/seek reads get their required blocks.
      multiConnectionOptions: { connections: 4, blockSizeMb: 1 }
    };
    this.lastPlayerState = PlayerState.IDLE;
    this.lastDiagnosticsAt = 0;
    const accepted = this.controller.load(source, options);
    if (accepted) {
      this.startSystemSession(command);
      // Warm the extractor while mfplayer prepares the same authenticated
      // source, so the first drag does not also pay metadata discovery cost.
      void this.previewHelper.prepare(url, command.headers);
    }
    hilog.info(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG,
      'load submitted accepted=%{public}s headers=%{public}d insecureTls=%{public}s multi=%{public}s',
      `${accepted}`, command.headers.length, `${command.allowInsecureTls}`, `${options.multiConnectionEnabled}`);
    if (!accepted) {
      this.emitEvent('error', undefined, 'engine');
    }
  }

  private handlePlayerEvent(event: PlayerEvent): void {
    if (this.usingSystem) return;
    this.syncGlassBackdrop();
    if (event.state === PlayerState.READY && this.fallbackReady) {
      this.fallbackReady = false;
      this.controller.seekTo(this.fallbackPosition, false);
      this.controller.setPlaybackSpeed(this.fallbackSpeed);
      if (this.fallbackPlaying) this.controller.play();
      this.lastPlayerState = PlayerState.READY;
      this.restoreFallbackTracks(event);
      this.emitEvent('tracks', event);
      this.emitProgress(true, event);
      return;
    }
    if (event.state !== this.lastPlayerState) {
      this.lastPlayerState = event.state;
      switch (event.state) {
        case PlayerState.READY:
          this.emitEvent('ready', event);
          // Some mfplayer backends expose their final track list at READY
          // without sending a separate TRACKS_CHANGED event.
          this.emitEvent('tracks', event);
          this.emitDiagnostics(true);
          break;
        case PlayerState.PLAYING:
          this.emitEvent('playing', event);
          break;
        case PlayerState.PAUSED:
          this.emitEvent('paused', event);
          break;
        case PlayerState.BUFFERING:
          this.emitEvent('buffering', event);
          break;
        case PlayerState.ENDED:
          this.emitProgress(true, event);
          this.emitEvent('ended', event);
          break;
        case PlayerState.ERROR:
          this.emitEvent('error', event, this.failureKind(event));
          break;
        default:
          break;
      }
    }
    if (event.type === PlayerEventType.TRACKS_CHANGED) {
      this.restoreFallbackTracks(event);
      this.emitEvent('tracks', event);
    }
    if (event.type === PlayerEventType.PROGRESS_CHANGED) {
      this.emitProgress(false, event);
      this.emitDiagnostics(false);
    }
    if (event.type === PlayerEventType.VIDEO_INFO_CHANGED || event.type === PlayerEventType.DECODER_CHANGED) {
      this.emitDiagnostics(true);
    }
  }

  private restoreFallbackTracks(event: PlayerEvent): void {
    if (!this.fallbackTracks) return;
    const audio = this.lastSystemTracks.filter((track: SystemEngineTrack): boolean => track.kind === 'audio');
    const subtitle = this.lastSystemTracks.filter((track: SystemEngineTrack): boolean => track.kind === 'subtitle');
    const selectedAudio = audio.findIndex((track: SystemEngineTrack): boolean => track.selected);
    const selectedSubtitle = subtitle.findIndex((track: SystemEngineTrack): boolean => track.selected);
    const audioTracks = event.audioTracks.length > 0 ? event.audioTracks : this.controller.getAudioTracks();
    const subtitleTracks = event.subtitleTracks.length > 0 ? event.subtitleTracks : this.controller.getSubtitleTracks();
    // Empty early TRACKS_CHANGED is not the final track inventory.
    if (selectedAudio >= audioTracks.length || selectedSubtitle >= subtitleTracks.length) return;
    this.fallbackTracks = false;
    if (selectedAudio >= 0) this.controller.selectAudioTrack(audioTracks[selectedAudio].id);
    if (selectedSubtitle >= 0) this.controller.selectSubtitleTrack(subtitleTracks[selectedSubtitle].id);
    else this.controller.selectSubtitleTrack(-1);
  }

  /** mfplayer runtime snapshot, matching the legacy player information card. */
  private emitDiagnostics(force: boolean): void {
    const now = Date.now();
    if (!force && now - this.lastDiagnosticsAt < 1000) {
      return;
    }
    this.lastDiagnosticsAt = now;
    try {
      const videoInfo: PlayerVideoInfo = this.controller.getVideoInfo();
      const stats = this.controller.getPlaybackStats();
      const diagnostics = this.controller.getMultiConnectionDiagnostics();
      const directDownloadSpeed = Math.max(0, this.controller.getDownloadSpeedBps());
      const aggregateDownloadSpeed = diagnostics !== undefined && !diagnostics.downgraded ?
        Math.max(0, diagnostics.aggregateSpeedBps) : 0;
      const payload: MaFeiPlayerEventState = {
        handle: this.args.handle,
        event: 'diagnostics',
        positionMs: 0,
        durationMs: 0,
        bufferedMs: 0,
        tracks: [],
        decodeMode: this.controller.getDecodeMode(),
        decoder: this.controller.getHardwareDecoder(),
        renderer: stats.currentVo ?? '',
        videoCodec: videoInfo.codec,
        videoCodecProfile: videoInfo.codecProfile,
        videoPixelFormat: videoInfo.pixelFormat,
        videoPrimaries: videoInfo.primaries,
        videoTransfer: videoInfo.transfer,
        outputPrimaries: videoInfo.outputPrimaries,
        outputTransfer: videoInfo.outputTransfer,
        dynamicRange: this.dynamicRangeLabel(videoInfo),
        videoWidthPx: Math.max(0, this.controller.getVideoWidth()),
        videoHeightPx: Math.max(0, this.controller.getVideoHeight()),
        videoBitRate: Math.max(0, stats.videoBitrate),
        audioBitRate: Math.max(0, stats.audioBitrate),
        audioSampleRateHz: Math.max(0, stats.audioSampleRate),
        audioChannels: Math.max(0, stats.audioChannels),
        renderedDropCount: Math.max(0, stats.voDropCount),
        decoderDropCount: Math.max(0, stats.decoderDropCount),
        delayedFrameCount: Math.max(0, stats.delayedCount),
        downloadSpeedBytesPerSecond: aggregateDownloadSpeed > 0 ? aggregateDownloadSpeed : directDownloadSpeed,
        bufferedDurationMs: Math.round(Math.max(0, this.controller.getBufferedDurationSec()) * 1000),
        networkSource: this.controller.isNetworkSource(),
        configuredConnections: diagnostics !== undefined && !diagnostics.downgraded ?
          Math.max(0, diagnostics.configuredConnections) : 0,
        activeConnections: diagnostics !== undefined && !diagnostics.downgraded ?
          Math.max(0, diagnostics.activeRequests) : 0
      };
      nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
    } catch (_error) {
      hilog.warn(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG, 'player diagnostics unavailable');
    }
  }

  private dynamicRangeLabel(info: PlayerVideoInfo): string {
    if (info.dolbyVisionProfile > 0) {
      return `Dolby Vision P${info.dolbyVisionProfile}`;
    }
    const range = `${info.transfer} ${info.primaries}`.trim().toUpperCase();
    if (range.includes('VIVID')) return 'HDR Vivid';
    if (range.includes('HDR10PLUS') || range.includes('HDR10+')) return 'HDR10+';
    if (range.includes('HDR10')) return 'HDR10';
    if (range.includes('HLG')) return 'HLG';
    if (info.hdr || range.includes('HDR') || range.includes('PQ') || range.includes('2084')) return 'HDR';
    return info.codec.trim().length > 0 ? 'SDR' : '';
  }

  private failureKind(event: PlayerEvent): string {
    if (event.errorCode === PlayerErrorCode.INVALID_SOURCE) return 'unsupported';
    const message = event.errorMessage.toLowerCase();
    if (message.includes('http') || message.includes('network') || message.includes('timeout')) return 'network';
    if (message.includes('decode') || message.includes('codec')) return 'decode';
    return 'engine';
  }

  private emitProgress(_force: boolean, event?: PlayerEvent): void {
    // CMP draws the timeline, so it needs the SDK's live 250 ms progress.
    // Provider progress reporting remains throttled by PlayerStateMachine.
    this.emitEvent('progress', event);
  }

  private emitEvent(type: string, event?: PlayerEvent, failure?: string): void {
    if (this.systemSession.casting.active) return;
    const includeTracks = type === 'tracks';
    const eventAudioTracks = event?.audioTracks ?? [];
    const eventSubtitleTracks = event?.subtitleTracks ?? [];
    const audioSource = eventAudioTracks.length > 0 ? eventAudioTracks : this.controller.getAudioTracks();
    const subtitleSource = eventSubtitleTracks.length > 0 ? eventSubtitleTracks : this.controller.getSubtitleTracks();
    const audioTracks = (includeTracks ? audioSource : []).map(
      (track: PlayerTrack): MaFeiPlayerTrackState => ({
        id: track.id,
        kind: 'audio',
        label: track.title.trim().length > 0 ? track.title : `音轨 ${track.id}`,
        language: track.language,
        selected: track.isSelected
      }));
    const subtitleTracks = (includeTracks ? subtitleSource : []).map(
      (track: PlayerTrack): MaFeiPlayerTrackState => ({
        id: track.id,
        kind: 'subtitle',
        label: track.title.trim().length > 0 ? track.title : `字幕 ${track.id}`,
        language: track.language,
        selected: track.isSelected
      }));
    if (includeTracks) {
      hilog.info(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG,
        'tracks audio=%{public}d subtitle=%{public}d', audioTracks.length, subtitleTracks.length);
    }
    const payload: MaFeiPlayerEventState = {
      handle: this.args.handle,
      event: type,
      positionMs: Math.round(Math.max(0, event?.positionMs ?? this.controller.getCurrentPositionMs())),
      durationMs: Math.round(Math.max(0, event?.durationMs ?? this.controller.getDurationMs())),
      bufferedMs: Math.round(Math.max(0,
        event?.bufferedPositionMs ?? this.controller.getBufferedPositionMs())),
      failure: failure,
      tracks: audioTracks.concat(subtitleTracks)
    };
    if (type !== 'tracks') {
      if (type !== 'progress') this.sessionPhase = type;
      const snapshot: SystemPlaybackSnapshot = {
        state: this.sessionPhase, positionMs: payload.positionMs, durationMs: payload.durationMs,
        bufferedMs: payload.bufferedMs, speed: this.args.speed
      };
      this.systemSession.update(this.sessionMetadata, snapshot);
      this.backgroundAudio.update(this.args.audioOnly, this.sessionPhase);
      this.pictureInPicture.update(this.pictureInPictureSnapshot());
    }
    nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
  }

  build() {
    Stack() {
      if (this.usingSystem) {
        XComponent({ id: 'mafei-system-player', type: XComponentType.SURFACE, controller: this.systemSurface })
          .width('100%').height('100%')
          .onLoad((): void => this.systemEngine.surface(this.systemSurface.getXComponentSurfaceId()))
          .onDestroy((): void => this.systemEngine.surface(''))
      } else {
        PlayerSurface({
        controller: this.controller,
        contentMode: this.contentMode,
        onVideoSurfaceControllerReady: (_controller: XComponentController): void => {
          this.mpvSurface = _controller;
          this.syncGlassBackdrop();
          // PlayerController keeps load() as a pending source until the video
          // surface is ready. Without this signal the native player is never
          // created and CMP remains in its loading state indefinitely.
          const accepted = this.controller.setVideoSurfaceReady(true);
          hilog.info(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG, 'surface ready accepted=%{public}s', `${accepted}`);
        }
      })
        .width('100%')
        .height('100%')
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
  }
}

export function registerMaFeiPlayerInteropBuilder(): void {
  registerComposeInteropBuilder('MaFeiPlayerSurface', mafeiPlayerSurfaceBuilder);
}
