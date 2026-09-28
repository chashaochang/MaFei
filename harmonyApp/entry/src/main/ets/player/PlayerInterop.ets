import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
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

interface MaFeiPlayerSurfaceArgs {
  commandId: number;
  handle: number;
  action: string;
  url: string;
  headers: string[];
  subtitleUrl: string;
  allowInsecureTls: boolean;
  multiConnectionAllowed: boolean;
  positionMs: number;
  speed: number;
  trackId: number;
  contentMode: string;
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
  private appliedCommandId: number = -1;
  private lastPlayerState: PlayerState = PlayerState.IDLE;
  private lastDiagnosticsAt: number = 0;
  private readonly listener: PlayerEventListener = {
    onPlayerEvent: (event: PlayerEvent): void => this.handlePlayerEvent(event)
  };
  private readonly onHostBackground = (): void => {
    this.controller.onHostBackground();
  };
  private readonly onHostForeground = (): void => {
    this.controller.onHostForeground();
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
    const context = getContext(this) as common.UIAbilityContext;
    this.emitProgress(true);
    context.eventHub.off('onBackground', this.onHostBackground);
    context.eventHub.off('onForeground', this.onHostForeground);
    this.controller.removeListener(this.listener);
    this.controller.release();
    void this.previewHelper.release();
  }

  onArgsChanged(): void {
    this.applyCommand();
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
        this.controller.stop();
        break;
      case 'seek':
        this.controller.seekTo(Math.max(0, command.positionMs), false);
        break;
      case 'speed':
        if (command.speed > 0) this.controller.setPlaybackSpeed(command.speed);
        break;
      case 'selectAudio':
        if (command.trackId >= 0) this.controller.selectAudioTrack(command.trackId);
        break;
      case 'selectSubtitle':
        this.controller.selectSubtitleTrack(command.trackId);
        break;
      case 'release':
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
      useHardwareDecode: true,
      // The HarmonyOS PC emulator does not expose a usable Vulkan surface;
      // the buffer/gpu path crashes inside libmpv during pl_vulkan_create.
      // Keep the native surface path for the OHOS host so the PC emulator and
      // real windowed devices use the same decoder/output route. CMP still
      // owns all visible controls above this surface.
      renderMode: PlayerRenderMode.SURFACE,
      // The shared PlayerStateMachine sends Play/Seek only after READY.
      autoPlay: false,
      startPositionMs: Math.max(0, command.positionMs),
      playbackSpeed: command.speed > 0 ? command.speed : 1,
      // The native view consumes smooth progress locally. KMP receives only
      // throttled progress reports for provider bookkeeping.
      progressIntervalMs: 250,
      exactSeek: false,
      multiConnectionEnabled: command.multiConnectionAllowed
    };
    this.lastPlayerState = PlayerState.IDLE;
    this.lastDiagnosticsAt = 0;
    const accepted = this.controller.load(source, options);
    if (accepted) {
      // Warm the extractor while mfplayer prepares the same authenticated
      // source, so the first drag does not also pay metadata discovery cost.
      void this.previewHelper.prepare(url, command.headers);
    }
    hilog.info(PLAYER_LOG_DOMAIN, PLAYER_LOG_TAG,
      'load submitted accepted=%{public}s headers=%{public}d insecureTls=%{public}s multi=%{public}s',
      `${accepted}`, command.headers.length, `${command.allowInsecureTls}`, `${command.multiConnectionAllowed}`);
    if (!accepted) {
      this.emitEvent('error', undefined, 'engine');
    }
  }

  private handlePlayerEvent(event: PlayerEvent): void {
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
    nativeApi.UpdateOhosPlayerState(JSON.stringify(payload));
  }

  build() {
    Stack() {
      PlayerSurface({
        controller: this.controller,
        contentMode: this.contentMode,
        onVideoSurfaceControllerReady: (_controller: XComponentController): void => {
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
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
  }
}

export function registerMaFeiPlayerInteropBuilder(): void {
  registerComposeInteropBuilder('MaFeiPlayerSurface', mafeiPlayerSurfaceBuilder);
}
