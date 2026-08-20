import { danDanPlayConfig } from "../../config/api-config";
import { BusinessError, emitter } from "@kit.BasicServicesKit";
import {
  MediaPlaybackItemKind,
  MediaPlaybackPreparedStreams,
  MediaPlaybackRequestHeader,
  MediaPlaybackSource,
  MediaPlaybackStream
} from "../../media/playback/MediaPlaybackModels";
import { MediaPlaybackProvider } from "../../media/playback/MediaPlaybackProvider";
import { currentMediaPlaybackProvider } from "../../media/MediaPlaybackAccess";
import {
  MediaProviderRegistry,
  MediaProviderScopedPlayback
} from "../../media/MediaProviderRegistry";
import { Constants } from "../../utils/Constants";
import axios, { AxiosError, AxiosHeaders, AxiosResponse } from "@ohos/axios";
import { SubtitleManager } from "../../utils/SubtitleManager";
import { QueueItem } from "../../entity/QueueItem";
import avSessionManager from "../../utils/AVSessionManager";
import { avSession } from "@kit.AVSessionKit";
import qualityOptionsProvider from "../../player/qualityoptions/QualityOptionsProvider";
import { BackgroundTaskManager } from "../../utils/BackgroundTaskManager";
import {
  BaseDanmaku,
  BaseDanmakuParser,
  Callback,
  DanmakuContext,
  DanmakuTimer,
  DANMAKU_STYLE_STROKEN,
  JSONSource
} from "@ohos/danmakuflamemaster";
import { PlayerPageUIState } from "./PlayerPageUIState";
import { QueueManager } from "../../player/queue/QueueManager";
import { PlaybackRequestResult } from "../../player/queue/PlaybackRequestResult";
import { PlayStatus } from "./PlayStatus";
import { TrackSelectionHelper } from "../../player/TrackSelectionHelper";
import { parseXmlToCommentData } from "./danmu/DanmuUtil";
import { CommentData, CommentResponseV2 } from "./danmu/CommentResponseV2";
import { DanDanPlayParser } from "./danmu/DandanPlayParser";
import { SearchBangumiResponse } from "./danmu/SearchBangumiResponse";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { AppPreference } from "../../data/AppPreference";
import { PlayerEngineType } from "../../entity/PlayerEngineType";
import { PlayerEngineDecision, PlayerEngineResolver } from "../../player/PlayerEngineResolver";
import { openUrlByBrowser } from "../../common/CommonFunc";
import { ToastUtil } from "../../utils/ToastUtil";
import { ReportPayload, ReportService } from "../../network/ReportService";

interface DanmakuUrlResponse {
  readonly url?: string
}

@ObservedV2
export class PlayerPageViewModel extends BaseViewModel<PlayerPageUIState> implements MediaProviderScopedPlayback {
  // 弹幕功能暂不上线，后续恢复时改回 true 并取消相关注释即可。
  private static readonly DANMAKU_ENABLED: boolean = true
  private static readonly CONTENT_REPORT_EMAIL: string = '1139396584@qq.com'
  private static readonly REPORT_DESCRIPTION_MAX_LENGTH: number = 100
  private static readonly PLAYBACK_PROGRESS_REPORT_INTERVAL_MS: number = 10000
  queueManager: QueueManager
  private readonly playbackProvider: MediaPlaybackProvider
  subtitleManager: SubtitleManager = new SubtitleManager()
  ui: PlayerPageUIState
  danmuContext: DanmakuContext
  private playbackStopInFlight: boolean = false
  private playbackStopTask?: Promise<void>
  private playbackStopReportedSessionId?: string
  private lastPlaybackProgressPositionMs: number = -1
  private scopedPlaybackRegistered: boolean = false

  constructor(ui: PlayerPageUIState,
    playbackProvider: MediaPlaybackProvider = currentMediaPlaybackProvider()) {
    super(ui)
    this.ui = ui
    this.playbackProvider = playbackProvider
    this.queueManager = new QueueManager(this, playbackProvider)
    MediaProviderRegistry.instance().registerScopedPlayback(
      this.playbackProvider.session.accountScope,
      this
    )
    this.scopedPlaybackRegistered = true
    this.danmuContext = DanmakuContext.create();
    this.ui.enableDanmu = false
    this.ui.danmuOpen = false
    // 弹幕功能暂不上线，先注释初始化。
    // this.danmuInit()
    if (PlayerPageViewModel.DANMAKU_ENABLED) {
      this.danmuInit()
    }
    this.syncContentReportState()
  }

  private buildContentReportPayload(description: string): ReportPayload {
    const mediaSource = this.mediaSourceOrNull
    return {
      title: mediaSource?.name || this.ui.title || '未知视频',
      itemId: mediaSource?.itemId || this.ui.currentPlayId || '',
      serverUrl: mediaSource?.serverUrl || 'unknown',
      source: 'player',
      description,
      playSessionId: mediaSource?.playSessionId || '',
      client: 'mafei-ohos',
      reporterId: this.playbackProvider.session.userId,
      reporterName: this.playbackProvider.session.displayName
    }
  }

  syncContentReportState(): void {
    const payload = this.buildContentReportPayload('')
    this.ui.isContentReported = ReportService.hasReported(payload.itemId, payload.serverUrl)
  }

  updateReportContentDraft(value: string): void {
    if (value.length > PlayerPageViewModel.REPORT_DESCRIPTION_MAX_LENGTH) {
      this.ui.reportContentDraft = value.substring(0, PlayerPageViewModel.REPORT_DESCRIPTION_MAX_LENGTH)
      return
    }
    this.ui.reportContentDraft = value
  }

  getReportDescriptionMaxLength(): number {
    return PlayerPageViewModel.REPORT_DESCRIPTION_MAX_LENGTH
  }

  getReportDescriptionLength(): number {
    return this.ui.reportContentDraft.length
  }

  canSubmitReport(): boolean {
    return !this.ui.isReportSubmitting && !this.ui.isContentReported && this.ui.reportContentDraft.trim().length > 0
  }

  showReportComposer(): void {
    if (this.ui.isReportSubmitting) {
      ToastUtil.showToast(this.ui.context, '举报提交中，请勿重复操作')
      return
    }
    this.syncContentReportState()
    if (this.ui.isContentReported) {
      ToastUtil.showToast(this.ui.context, '该内容已举报过')
      return
    }
    this.ui.isReportSheetVisible = true
  }

  hideReportComposer(clearDraft: boolean): void {
    this.ui.isReportSheetVisible = false
    if (clearDraft) {
      this.ui.reportContentDraft = ''
    }
  }

  private formatDuration(runTimeTicks?: number | null): string {
    if (!runTimeTicks || runTimeTicks <= 0) {
      return ''
    }
    const totalSeconds = Math.floor(runTimeTicks / Constants.TICKS_PER_MILLISECOND / 1000)
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    const parts: string[] = []
    if (hours > 0) {
      parts.push(`${hours}小时`)
    }
    if (minutes > 0) {
      parts.push(`${minutes}分钟`)
    }
    if (seconds > 0 && hours === 0) {
      parts.push(`${seconds}秒`)
    }
    return parts.join('') || '0秒'
  }

  danmuInit() {
    let maxLinesPair: Map<number, number> = new Map();
    maxLinesPair.set(BaseDanmaku.TYPE_SCROLL_RL, 11); // 滚动弹幕最大显示5行
    // 设置是否禁止重叠
    let overlappingEnablePair: Map<number, boolean> = new Map();
    overlappingEnablePair.set(BaseDanmaku.TYPE_SCROLL_RL, true);
    overlappingEnablePair.set(BaseDanmaku.TYPE_FIX_TOP, true);
    overlappingEnablePair.set(BaseDanmaku.TYPE_FIX_BOTTOM, true)

    this.danmuContext.setDanmakuStyle(DANMAKU_STYLE_STROKEN, 3)
      .setDuplicateMergingEnabled(false)
      // .setScrollSpeedFactor(1.2)
      // .setScaleTextSize(1.2)
      // .setCacheStuffer(new SpannedCacheStuffer(), this.mCacheStufferAdapter)// 图文混排使用SpannedCacheStuffer
      .setMaximumLines(maxLinesPair)
      .preventOverlapping(overlappingEnablePair)
      .setDanmakuMargin(4)
      .setScaleTextSize(this.appUIState.isFullScreen? 1 : 0.8)
  }

  private getConfiguredEngineType(): PlayerEngineType {
    return AppPreference.getInstance().getPlayerEngineType()
  }

  getResolvedPlayerEngineDecision(source?: MediaPlaybackSource): PlayerEngineDecision {
    return PlayerEngineResolver.resolvePlaybackEngine(this.getConfiguredEngineType(), source ?? this.mediaSourceOrNull)
  }

  getResolvedPlayerEngineType(source?: MediaPlaybackSource): PlayerEngineType {
    return this.getResolvedPlayerEngineDecision(source).engineType
  }

  private isMpvEngine(source?: MediaPlaybackSource): boolean {
    return this.getResolvedPlayerEngineType(source) === PlayerEngineType.MPV
  }

  private getCurrentPlaybackPositionMs(): number {
    if (this.ui.playbackPositionMs > 0) {
      return this.ui.playbackPositionMs
    }
    return this.mediaSourceOrNull?.startTimeMs ?? 0
  }

  private getCurrentPlaybackDurationMs(): number {
    const runtimeTicks = this.mediaSourceOrNull?.runTimeTicks ?? 0
    if (runtimeTicks > 0) {
      return runtimeTicks / Constants.TICKS_PER_MILLISECOND
    }
    return 0
  }

  private openContentReportFallback(
    mediaTitle: string,
    itemId: string,
    server: string,
    playSessionId: string,
  ): void {
    const body = [
      '请描述您要举报的问题内容：',
      '',
      `影片名称：${mediaTitle}`,
      `媒体 ID：${itemId}`,
      `播放会话：${playSessionId}`,
      `服务器地址：${server}`,
    ].join('\n')
    const mailto =
      `mailto:${PlayerPageViewModel.CONTENT_REPORT_EMAIL}` +
      `?subject=${encodeURIComponent(`马飞内容举报：${mediaTitle}`)}` +
      `&body=${encodeURIComponent(body)}`
    openUrlByBrowser(mailto)
    ToastUtil.showToast(this.ui.context, '未配置举报服务，已打开邮件举报')
  }

  async submitContentReport(): Promise<void> {
    const reportContent = this.ui.reportContentDraft.trim()
    if (reportContent.length === 0) {
      ToastUtil.showToast(this.ui.context, '请输入举报内容')
      return
    }
    const payload = this.buildContentReportPayload(reportContent)
    if (this.ui.isReportSubmitting) {
      ToastUtil.showToast(this.ui.context, '举报提交中，请勿重复操作')
      return
    }
    if (ReportService.hasReported(payload.itemId, payload.serverUrl)) {
      this.ui.isContentReported = true
      ToastUtil.showToast(this.ui.context, '该内容已举报过')
      return
    }
    if (!ReportService.isEnabled()) {
      this.openContentReportFallback(payload.title, payload.itemId || 'unknown', payload.serverUrl || 'unknown',
        payload.playSessionId || 'unknown')
      return
    }
    this.ui.isReportSubmitting = true
    try {
      const result = await ReportService.submitReport(payload)
      if (result.ok || result.alreadyReported) {
        this.ui.isContentReported = true
        this.hideReportComposer(true)
      }
      ToastUtil.showToast(this.ui.context, result.message)
    } finally {
      this.ui.isReportSubmitting = false
    }
  }

  @Monitor('mediaSourceOrNull')
  onMediaSourceChanged() {
    let source = this.mediaSourceOrNull
    if (source) {
      const engineDecision = this.getResolvedPlayerEngineDecision(source)
      this.ui.effectivePlayerEngine = engineDecision.engineType
      this.ui.effectivePlayerEngineReason = engineDecision.reason
      this.ui.forceMpvHardwareDecode = engineDecision.forceMpvHardwareDecode
      this.ui.forceMpvSoftwareDecode = engineDecision.forceMpvSoftwareDecode
      console.warn(`[PlayerVM] source changed configuredEngine=${this.getConfiguredEngineType()}, resolvedEngine=${engineDecision.engineType}, reason=${engineDecision.reason}, subtitleCount=${source.subtitleStreams.length}, selectedSubtitle=${source.selectedSubtitleStream?.Index ?? 'none'}, defaultSubtitle=${source.sourceInfo.DefaultSubtitleStreamIndex ?? 'null'}, videoRange=${source.selectedVideoRangeType}, dvProfile=${source.selectedVideoDvProfile ?? 'none'}, hdrStrategy=${source.getVideoHdrStrategyLabel()}`)
      this.ui.audioTrackIndex = source.audioStreamIndex
      this.ui.title = source.name
      this.ui.seriesName = source.item.SeriesName
      this.ui.audioStreams = source.audioStreams
      this.ui.subtitleStreams = source.subtitleStreams
      this.ui.selectedAudioStream = source.selectedAudioStream
      this.ui.selectedSubtitleStream = source.selectedSubtitleStream
      let videoStream = source.selectedVideoStream
      let height = videoStream?.Height
      let width = videoStream?.Width
      if (height != null && width != null) {
        this.ui.qualityOptions = qualityOptionsProvider.getApplicableQualityOptions(width, height)
      } else {
        //qualityButton.isVisible = false
      }
      this.ui.enableDanmu = false
      this.ui.danmuOpen = false
      // 弹幕功能暂不上线，先注释自动搜索。
      // if (!this.isMpvEngine(source)) {
      //   this.searchDanmuku()
      // }
      if (PlayerPageViewModel.DANMAKU_ENABLED && !this.isMpvEngine(source)) {
        this.searchDanmuku()
      }
    }
    this.syncContentReportState()
  }

  @Monitor('mediaSourceOrNull._selectedAudioStream')
  onSelectedAudioStreamChanged() {
    this.ui.selectedAudioStream = this.mediaSourceOrNull?._selectedAudioStream
  }

  @Monitor('mediaSourceOrNull._selectedSubtitleStream')
  onSelectedSubtitleStreamChanged() {
    this.ui.selectedSubtitleStream = this.mediaSourceOrNull?._selectedSubtitleStream
  }

  async initAVSession() {
    let session = await avSessionManager.initAVSession()
    this.setSessionCallBack(session)
    await session.activate().catch((err: BusinessError) => {
      if (err) {
        console.error(`Failed to activate AVSession, error info: ${JSON.stringify(err)}`);
      }
    });
  }

  setSessionCallBack(session: avSession.AVSession) {
    // 一般在监听器中会对播放器做相应逻辑处理
    // 不要忘记处理完后需要通过set接口同步播放相关信息，参考上面的用例
    session.on('play', () => {
      console.info(`on play , do play task`);
      // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('play')取消监听
      // 处理完毕后，请使用SetAVPlayState上报播放状态
      emitter.emit(Constants.PLAYER_EVENT, {
        data: {
          'eventName': 'Play'
        }
      })
    });
    session.on('pause', () => {
      console.info(`on pause , do pause task`);
      // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('pause')取消监听
      // 处理完毕后，请使用SetAVPlayState上报播放状态
      emitter.emit(Constants.PLAYER_EVENT, {
        data: {
          'eventName': 'Pause'
        }
      })
    });
    session.on('stop', () => {
      console.info(`on stop , do stop task`);
      // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('stop')取消监听
      // 处理完毕后，请使用SetAVPlayState上报播放状态
      emitter.emit(Constants.PLAYER_EVENT, {
        data: {
          'eventName': 'Stop'
        }
      })
    });
    // session.on('playNext', () => {
    //   console.info(`on playNext , do playNext task`);
    //   // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('playNext')取消监听
    //   // 处理完毕后，请使用SetAVPlayState上报播放状态，使用SetAVMetadata上报媒体信息
    //   this.queueManager.next()
    // });
    // session.on('playPrevious', () => {
    //   console.info(`on playPrevious , do playPrevious task`);
    //   // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('playPrevious')取消监听
    //   // 处理完毕后，请使用SetAVPlayState上报播放状态，使用SetAVMetadata上报媒体信息
    //   this.queueManager.previous()
    // });
    // session.on('fastForward', () => {
    //   console.info(`on fastForward , do fastForward task`);
    //   // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('fastForward')取消监听
    //   // 处理完毕后，请使用SetAVPlayState上报播放状态和播放position
    // });
    // session.on('rewind', () => {
    //   console.info(`on rewind , do rewind task`);
    //   // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('rewind')取消监听
    //   // 处理完毕后，请使用SetAVPlayState上报播放状态和播放position
    // });
    session.on('seek', (time) => {
      console.info(`on seek , the seek time is ${time}`);
      // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('seek')取消监听
      // 处理完毕后，请使用SetAVPlayState上报播放状态和播放position
      emitter.emit(Constants.PLAYER_EVENT, {
        data: {
          'eventName': 'Seek',
          'args': time
        }
      })
    });
    session.on('setSpeed', (speed) => {
      console.info(`on setSpeed , the speed is ${speed}`);
      // do some tasks ···
      emitter.emit(Constants.PLAYER_EVENT, {
        data: {
          'eventName': 'Speed',
          'args': speed + 'f'
        }
      })
    });
    // session.on('toggleFavorite', (assetId) => {
    //   console.info(`on toggleFavorite , the target asset Id is ${assetId}`);
    //   // 如暂不支持该指令，请勿注册；或在注册后但暂不使用时，通过session.off('toggleFavorite')取消监听
    //   // 处理完毕后，请使用SetAVPlayState上报收藏结果isFavorite
    // });
    session.on('outputDeviceChange', this.sessionOutputDeviceChange)
  }

  private sessionOutputDeviceChange = async (connectState: avSession.ConnectionState,
    device: avSession.OutputDeviceInfo) => {
    let currentDevice: avSession.DeviceInfo = device?.devices?.[0];
    if (currentDevice.castCategory === avSession.AVCastCategory.CATEGORY_REMOTE &&
      connectState === avSession.ConnectionState.STATE_CONNECTED) {
      if (currentDevice.supportedProtocols === avSession.ProtocolType.TYPE_CAST_PLUS_STREAM) {
        // 此设备支持cast+投播协议
        console.log('此设备支持cast+投播协议')
      } else if (currentDevice.supportedProtocols === avSession.ProtocolType.TYPE_DLNA) {
        // 此设备支持DLNA投播协议
        console.log('此设备支持DLNA投播协议')
      }
      this.startCast()
    } else if ((currentDevice.castCategory === avSession.AVCastCategory.CATEGORY_REMOTE &&
      connectState === avSession.ConnectionState.STATE_DISCONNECTED) ||
      (currentDevice.castCategory === avSession.AVCastCategory.CATEGORY_LOCAL &&
        connectState === avSession.ConnectionState.STATE_CONNECTED)) {
      if (device) {
        this.stopCast()
      }
    }
  };

  @Monitor('uiState.CONTROL_PlayStatus')
  async onPlayStateChanged() {
    if (this.ui.CONTROL_PlayStatus == PlayStatus.INIT) {

    }
  }

  get mediaSourceOrNull(): MediaPlaybackSource | undefined {
    return this.queueManager.currentMediaSourceOrNull
  }

  trackSelectionHelper: TrackSelectionHelper = new TrackSelectionHelper(this)
  initialTracksSelected: boolean = false
  //由于这个player要设置完url才能得到,暂时保存初始化信息
  initSource?: MediaPlaybackSource

  load(source: MediaPlaybackSource, streams: MediaPlaybackPreparedStreams, playWhenReady: boolean) {
    this.initSource = source
    this.playbackStopInFlight = false
    this.playbackStopReportedSessionId = undefined
    this.lastPlaybackProgressPositionMs = -1
    this.ui.selectedBitrate = source.maxStreamingBitrate
    this.ui.mediaType = source.item.Type ?? MediaPlaybackItemKind.Unknown
    this.ui.currentPlayId = source.ref.id
    this.ui.videoHeaders = streams.videoHeaders.slice()
    this.ui.url = streams.videoUrl
    this.ui.playWhenReady = playWhenReady
    this.initialTracksSelected = false
    const startTime = source.startTimeMs
    if (startTime > 0) {
      this.ui.initSeek = startTime
    }
    if (source.item.Type == MediaPlaybackItemKind.Episode) {
      void this.queueManager.expandEpisodeQueueIfNeeded().then((): void => {
        void this.getItems()
        this.ui.playerMenus?.onQueueItemChanged(source, this.queueManager.hasNext())
      })
      avSessionManager.getSession()?.setAVQueueTitle(source.item.SeriesName ?? source.item.Name ?? 'TITLE')
    }
    avSessionManager.getSession()?.setAVMetadata(source.toMediaMetadata())
    this.reportPlaybackStart(source)
    for (const subtitle of streams.externalSubtitles) {
      console.log(`外挂字幕:${subtitle.deliveryUrl}`)
    }
  }

  reportPlaybackStart(mediaSource: MediaPlaybackSource): void {
    void this.playbackProvider.lifecycle.reportStart(mediaSource).catch((error: Error): void => {
      console.error(`[PlayerVM] reportPlaybackStart failed: ${JSON.stringify(error)}`)
    })
  }

  reportPlaybackProgress(positionMs: number, paused: boolean = false, force: boolean = false): void {
    const mediaSource = this.mediaSourceOrNull
    if (!mediaSource || !Number.isFinite(positionMs) || positionMs < 0) {
      return
    }
    if (!force && this.lastPlaybackProgressPositionMs >= 0 &&
      Math.abs(positionMs - this.lastPlaybackProgressPositionMs) <
        PlayerPageViewModel.PLAYBACK_PROGRESS_REPORT_INTERVAL_MS) {
      return
    }
    this.lastPlaybackProgressPositionMs = positionMs
    void this.playbackProvider.lifecycle.reportProgress(mediaSource, {
      positionTicks: Math.floor(positionMs * Constants.TICKS_PER_MILLISECOND),
      paused: paused,
      muted: false
    }).catch((error: Error): void => {
      console.error(`[PlayerVM] reportPlaybackProgress failed: ${JSON.stringify(error)}`)
    })
  }

  async reportPlaybackStop(): Promise<void> {
    if (this.playbackStopTask) {
      await this.playbackStopTask
      return
    }
    const task = this.performPlaybackStop()
    this.playbackStopTask = task
    try {
      await task
    } finally {
      if (this.playbackStopTask === task) {
        this.playbackStopTask = undefined
      }
    }
  }

  private async performPlaybackStop(): Promise<void> {
    const mediaSource = this.mediaSourceOrNull
    const currentPositionMs = this.getCurrentPlaybackPositionMs()
    const totalDurationTicks = mediaSource?.runTimeTicks ?? 0
    const totalDurationMs = totalDurationTicks > 0 ? Math.floor(totalDurationTicks / Constants.TICKS_PER_MILLISECOND) : 0
    const totalDurationText = this.formatDuration(totalDurationTicks)
    const currentPositionText = this.formatDuration(currentPositionMs * Constants.TICKS_PER_MILLISECOND)
    console.info(
      `[PlayerVM] reportPlaybackStop called: hasMediaSource=${!!mediaSource}, playStatus=${this.ui.CONTROL_PlayStatus}, currentPositionMs=${currentPositionMs}, currentPosition=${currentPositionText}, totalDurationMs=${totalDurationMs}, totalDuration=${totalDurationText}, inFlight=${this.playbackStopInFlight}`
    )
    if (!mediaSource) {
      console.warn('[PlayerVM] reportPlaybackStop skipped: mediaSource is null')
      return
    }
    const playSessionId = mediaSource.playSessionId ?? ''
    if (this.playbackStopInFlight) {
      console.warn(`[PlayerVM] reportPlaybackStop skipped: request already in flight, itemId=${mediaSource.itemId}, playSessionId=${playSessionId}`)
      return
    }
    if (playSessionId.length > 0 && this.playbackStopReportedSessionId === playSessionId) {
      console.warn(`[PlayerVM] reportPlaybackStop skipped: session already reported, itemId=${mediaSource.itemId}, playSessionId=${playSessionId}`)
      await this.closePlaybackTranscode(mediaSource)
      return
    }
    const hasFinished = this.ui.CONTROL_PlayStatus == PlayStatus.DONE
    const lastPositionTicks =
      hasFinished ? mediaSource.runTimeTicks :
        currentPositionMs * Constants.TICKS_PER_MILLISECOND
    const reportedPositionMs = Math.floor(lastPositionTicks / Constants.TICKS_PER_MILLISECOND)
    const reportedPositionText = this.formatDuration(lastPositionTicks)
    this.playbackStopInFlight = true
    console.info(
      `[PlayerVM] reportPlaybackStop request: itemId=${mediaSource.itemId}, playSessionId=${playSessionId}, hasFinished=${hasFinished}, positionTicks=${lastPositionTicks}, positionMs=${reportedPositionMs}, position=${reportedPositionText}, totalDurationMs=${totalDurationMs}, totalDuration=${totalDurationText}, liveStreamId=${mediaSource.liveStreamId ?? 'none'}`
    )
    try {
      await this.playbackProvider.lifecycle.reportStop(mediaSource, {
        positionTicks: lastPositionTicks,
        failed: false
      })
      console.info(
        `[PlayerVM] reportPlaybackStop success: itemId=${mediaSource.itemId}, playSessionId=${playSessionId}, hasFinished=${hasFinished}, positionTicks=${lastPositionTicks}, positionMs=${reportedPositionMs}, position=${reportedPositionText}, totalDurationMs=${totalDurationMs}, totalDuration=${totalDurationText}`
      )
      if (playSessionId.length > 0) {
        this.playbackStopReportedSessionId = playSessionId
      }
      if (hasFinished) {
        await this.playbackProvider.lifecycle.reportCompleted(mediaSource)
        console.info(`[PlayerVM] markPlayedItem success: itemId=${mediaSource.itemId}, playSessionId=${playSessionId}`)
      }
    } catch (error) {
      console.error(`[PlayerVM] reportPlaybackStop failed: ${JSON.stringify(error)}`)
    } finally {
      this.playbackStopInFlight = false
    }
    await this.closePlaybackTranscode(mediaSource)
  }

  private async closePlaybackTranscode(mediaSource: MediaPlaybackSource): Promise<void> {
    try {
      await this.playbackProvider.lifecycle.closeTranscode(mediaSource)
    } catch (error) {
      console.error(`[PlayerVM] closeTranscode failed: ${JSON.stringify(error)}`)
    }
  }

  async selectSubtitle(selectedSubtitleStream: MediaPlaybackStream): Promise<void> {
    const source = this.initSource
    const codec = selectedSubtitleStream.Codec
    if (!source || !codec) {
      return
    }
    try {
      const text = await this.playbackProvider.loadSubtitle(source, selectedSubtitleStream)
      this.ui.subtitles = this.subtitleManager.parse(text, codec)
    } catch (error) {
      console.error(`[PlayerVM] load subtitle failed: ${JSON.stringify(error)}`)
    }
  }

  async getSubtitle(source: MediaPlaybackSource): Promise<void> {
    this.ui.currentSubtitle = ''
    this.ui.subtitles = []
    if (this.isMpvEngine(source)) {
      return
    }
    if (source.usesServerTranscoding()) {
      return
    }
    const selectedSubtitle = source.selectedSubtitleStream
    const codec = selectedSubtitle?.Codec
    if (!selectedSubtitle || !codec) {
      return
    }
    try {
      const text = await this.playbackProvider.loadSubtitle(source, selectedSubtitle)
      this.ui.subtitles = this.subtitleManager.parse(text, codec)
    } catch (error) {
      console.error(`[PlayerVM] load initial subtitle failed: ${JSON.stringify(error)}`)
    }
  }

  //获取播放列表信息
  async getItems(): Promise<void> {
    try {
      const items = await this.queueManager.getItems()
      if (this.queueManager.currentQueue.length === 0) {
        return
      }
      const list: QueueItem[] = []
      const sessionQueue: avSession.AVQueueItem[] = []
      for (const item of items) {
        const queueItem: QueueItem = {
          id: item.ref.id,
          playIndex: item.playIndex,
          name: item.name,
          image: item.image?.url || '',
          imageRef: item.image,
          durationText: this.formatDuration(item.durationTicks),
          played: item.played
        }
        list.push(queueItem)
        const description: avSession.AVMediaDescription = {
          assetId: item.ref.identity,
          title: item.seriesName || item.name || 'TITLE',
          subtitle: item.seriesName ? item.name : ''
        }
        if (item.image && item.image.headers.length === 0) {
          description.mediaImage = item.image.url
        }
        const avQueueItem: avSession.AVQueueItem = {
          itemId: item.playIndex,
          description: description
        }
        sessionQueue.push(avQueueItem)
      }
      this.ui.playList = list
      avSessionManager.getSession()?.setAVQueueItems(sessionQueue)
      this.ui.sessionQueue = sessionQueue
      const currentIndex = this.ui.playList.findIndex((item: QueueItem): boolean =>
        item.id === this.ui.currentPlayId)
      if (currentIndex >= 0) {
        this.ui.scroller.scrollToIndex(currentIndex)
      }
    } catch (error) {
      console.error(`[PlayerVM] load queue items failed: ${JSON.stringify(error)}`)
    }
  }

  cancelPendingPlayback(): void {
    this.queueManager.cancelPendingPlayback()
  }

  async stopForScope(accountScope: string): Promise<void> {
    if (accountScope !== this.playbackProvider.session.accountScope) {
      return
    }
    this.cancelPendingPlayback()
    const stopTask = this.reportPlaybackStop()
    emitter.emit(Constants.PLAYER_EVENT, {
      data: {
        'eventName': 'Stop'
      }
    })
    await stopTask
    this.clearPlaybackSnapshot()
  }

  private clearPlaybackSnapshot(): void {
    this.cancelPendingPlayback()
    this.ui.videoHeaders = []
    this.ui.url = undefined
    this.initSource = undefined
    this.lastPlaybackProgressPositionMs = -1
  }

  release(): void {
    if (this.scopedPlaybackRegistered) {
      MediaProviderRegistry.instance().unregisterScopedPlayback(
        this.playbackProvider.session.accountScope,
        this
      )
      this.scopedPlaybackRegistered = false
    }
    this.clearPlaybackSnapshot()
    avSessionManager.unregisterSessionListener()
    //BackgroundTaskManager.stopContinuousTask(this.context);
  }

  getPositionAndPause(): number {
    const position = this.getCurrentPlaybackPositionMs()
    if (!Number.isFinite(position) || position < 0) {
      return position
    }
    emitter.emit(Constants.PLAYER_EVENT, {
      data: {
        'eventName': 'Pause'
      }
    })
    return position
  }

  resumePlaybackAfterFailedRequest(): void {
    emitter.emit(Constants.PLAYER_EVENT, {
      data: {
        'eventName': 'Resume'
      }
    })
  }

  private handlePlaybackRequestResult(result: PlaybackRequestResult, failureMessage: string): boolean {
    if (result === PlaybackRequestResult.Success) {
      return true
    }
    if (result === PlaybackRequestResult.Failure) {
      ToastUtil.showToast(this.ui.context, failureMessage)
    }
    return false
  }

  async changeBitrate(bitrate?: number): Promise<boolean> {
    const result = await this.queueManager.changeBitrate(bitrate)
    return this.handlePlaybackRequestResult(result, '清晰度切换失败，已继续播放当前视频')
  }

  async selectAudioTrack(stream: MediaPlaybackStream): Promise<boolean> {
    const result = await this.trackSelectionHelper.selectAudioTrack(stream)
    return this.handlePlaybackRequestResult(result, '音轨切换失败，已继续播放当前视频')
  }

  async selectSubtitleTrack(stream: MediaPlaybackStream): Promise<boolean> {
    const result = await this.trackSelectionHelper.selectSubtitleTrack(stream)
    return this.handlePlaybackRequestResult(result, '字幕切换失败，已继续播放当前视频')
  }

  logTracks() {

  }

  private async startCast() {
    emitter.emit(Constants.PLAYER_EVENT, {
      data: {
        'eventName': 'Pause'
      }
    })
    let castController = await avSessionManager.getSession()?.getAVCastController()
    castController?.on('playbackStateChange', 'all', this.playbackStateChangeListener);
    castController?.on('endOfStream', this.reloadCasting);
    castController?.on('playNext', this.playNextCallback);
    castController?.on('playPrevious', this.playPreviousCallback);
    let queueItem = this.ui.playList.find(e => e.id == this.ui.currentPlayId)
    let avQueueItem: avSession.AVQueueItem = {
      itemId: queueItem!.playIndex,
      description: {
        assetId: queueItem!.id,
        title: this.ui.seriesName ?? this.ui.title,
        subtitle: this.ui.seriesName ? this.ui.title : '',
        mediaUri: this.ui.url,

        mediaType: 'VIDEO',
        appName: Constants.APP_INFO_NAME,
        //startPosition为投播当前进度，设置该字段可将本机播放进度同步到远端
        startPosition: this.getCurrentPlaybackPositionMs(),
        // 投播资源播放时长，设置该字段可将本机播放时长同步到远端显示
        duration: this.getCurrentPlaybackDurationMs(),
      }
    }

    try {
      await castController?.prepare(avQueueItem)
      await castController?.start(avQueueItem)
    } catch (e) {
      console.error(e)
    }
    BackgroundTaskManager.startContinuousTask(AppStorage.get('context'))
    this.ui.isCastPlaying = true;
  }

  async stopCast() {
    avSessionManager.getSession()?.stopCasting()
    this.ui.isCastPlaying = false;
    let castController = await avSessionManager.getSession()?.getAVCastController()
    castController?.off('playbackStateChange');
    castController?.off('endOfStream');
    castController?.off('playNext');
    castController?.off('playPrevious');
    BackgroundTaskManager.stopContinuousTask(AppStorage.get('context'))
  }

  private playbackStateChangeListener = (playbackState: avSession.AVPlaybackState) => {
    const duration = playbackState?.extras?.duration;
    // if (typeof duration === 'number') {
    //   this.playDurationChange(duration as number);
    // }

    if (typeof playbackState?.position?.elapsedTime !== 'undefined') {
      //this.positionChange(playbackState?.position?.elapsedTime);
      emitter.emit(Constants.PLAYER_EVENT, {
        data: {
          'eventName': 'Seek',
          'args': playbackState?.position?.elapsedTime
        }
      })
    }

    if (playbackState.state === Constants.PLAYBACK_STATE_PAUSE ||
      playbackState.state === Constants.PLAYBACK_STATE_STOP ||
      playbackState.state === Constants.PLAYBACK_STATE_PREPARE ||
      playbackState.state === Constants.PLAYBACK_STATE_INITIAL) {
      this.ui.isCastPlaying = false;
    }

    if (playbackState.state === Constants.PLAYBACK_STATE_PLAY) {
      this.ui.isCastPlaying = true;
    }
  };
  private reloadCasting = async () => {
    const queueItem = this.ui.playList.find((item: QueueItem): boolean => item.id == this.ui.currentPlayId)
    if (!queueItem) {
      return
    }
    const description: avSession.AVMediaDescription = {
      assetId: queueItem.id,
      title: (this.ui.mediaType == MediaPlaybackItemKind.Episode ? this.ui.seriesName : this.ui.title) ?? '',
      subtitle: (this.ui.mediaType == MediaPlaybackItemKind.Episode ? this.ui.title : '') ?? '',
      mediaUri: this.ui.url,
      mediaType: 'VIDEO',
      appName: Constants.APP_INFO_NAME,
      startPosition: 0,
      duration: this.getCurrentPlaybackDurationMs()
    }
    if (queueItem.imageRef && queueItem.imageRef.headers.length === 0) {
      description.mediaImage = queueItem.imageRef.url
    }
    const avQueueItem: avSession.AVQueueItem = {
      itemId: queueItem!.playIndex,
      description: description
    }

    try {
      let castController = await avSessionManager.getSession()?.getAVCastController()
      await castController?.prepare(avQueueItem)
      await castController?.start(avQueueItem)
    } catch (e) {
      console.error(e)
    }
    this.ui.isCastPlaying = true;
  };
  public playNextCallback = (): void => {
    this.playNext()
  };
  public playPreviousCallback = (): void => {
    this.playPrevious()
  };

  async playNext(): Promise<boolean> {
    const result = await this.queueManager.next()
    return this.handlePlaybackRequestResult(result, '无法播放下一项，已继续播放当前视频')
  }

  async playPrevious(): Promise<boolean> {
    const result = await this.queueManager.previous()
    return this.handlePlaybackRequestResult(result, '无法播放上一项，已继续播放当前视频')
  }

  async changeEpisode(index: number): Promise<boolean> {
    const result = await this.queueManager.changeEpisode(index)
    return this.handlePlaybackRequestResult(result, '选集失败，已继续播放当前视频')
  }

  private createAxiosHeaders(headers: MediaPlaybackRequestHeader[]): AxiosHeaders {
    const result = new AxiosHeaders()
    for (const header of headers) {
      if (header.name.trim().length > 0) {
        result.set(header.name, header.value)
      }
    }
    return result
  }

  async searchDanmuku() {
    const source = this.mediaSourceOrNull
    if (!PlayerPageViewModel.DANMAKU_ENABLED || !source?.capabilities.danmakuAvailable) {
      this.ui.enableDanmu = false
      this.ui.danmuOpen = false
      return
    }
    let that = this
    //先从弹幕插件api获取
    const resource = source.danmakuResource
    if (resource) {
      try {
        const urlRes: AxiosResponse<DanmakuUrlResponse> = await axios.get<DanmakuUrlResponse>(resource.url, {
          headers: this.createAxiosHeaders(resource.headers)
        })
        const url = urlRes.data.url || ''
        if (url.length > 0) {
          const xmlRes: AxiosResponse<string> = await axios.get<string>(url)
          if (xmlRes.data.length > 0) {
            const comments: CommentData[] = parseXmlToCommentData(xmlRes.data)
            if (this.ui.danmuModel != null) {
              const parser: BaseDanmakuParser = new DanDanPlayParser()
              const jsonSource = new JSONSource(JSON.stringify(comments));
              parser.load(jsonSource);
              this.ui.danmuModel.prepare(parser, this.danmuContext);
              this.ui.danmuModel.setCallback(new Call(that));
              this.onPlayStateChanged()
              return
            }
          }
        }
      } catch (error) {
        console.log(error)
      }
    }
    //插件api没有的从弹弹PlayApi获取
    const anime = encodeURIComponent(source.item.SeriesName || '')
    const episode = source.item.IndexNumber
    axios.get<SearchBangumiResponse>(`${danDanPlayConfig.baseUrl}/search/episodes?anime=${anime}&episode=${episode}`,
      {
        headers: {
          "X-AppId": danDanPlayConfig.appId,
          "X-AppSecret": danDanPlayConfig.appSecret
        }
      }).then((res: AxiosResponse<SearchBangumiResponse>) => {
      if (res.data.success && res.data.animes.length > 0) {
        let episodeId = res.data.animes[0].episodes[0].episodeId
        axios.get<CommentResponseV2>(`${danDanPlayConfig.baseUrl}/comment/${episodeId}?withRelated=true`, {
          headers: {
            "X-AppId": danDanPlayConfig.appId,
            "X-AppSecret": danDanPlayConfig.appSecret
          }
        }).then((res: AxiosResponse<CommentResponseV2>) => {
          if (res.data.count > 0 && res.data.comments.length > 0) {
            if (this.ui.danmuModel != null) {
              let parser: BaseDanmakuParser = new DanDanPlayParser()
              let jsonSource = new JSONSource(JSON.stringify(res.data.comments));
              parser.load(jsonSource);
              this.ui.danmuModel.prepare(parser, this.danmuContext);
              this.ui.danmuModel.setCallback(new Call(that));
              this.onPlayStateChanged()
            }
          }
        }).catch((e: AxiosError) => {
          console.log(e.message);
        })
      }
    }).catch((e: AxiosError) => {
      console.log(e.message);
    })
  }

  changeDanmuState() {
    if (!PlayerPageViewModel.DANMAKU_ENABLED) {
      this.ui.enableDanmu = false
      this.ui.danmuOpen = false
      return
    }
    if (this.appUIState.isFullScreen) {
      this.ui.danmuModel.getConfig().setScaleTextSize(1)
    } else {
      this.ui.danmuModel.getConfig().setScaleTextSize(0.6)
    }
  }
}

class Call implements Callback {
  private that: ESObject;

  constructor(that: ESObject) {
    this.that = that
  }

  public updateTimer(timer: DanmakuTimer): void {
  }

  public drawingFinished(): void {

  }

  public danmakuShown(danmaku: BaseDanmaku): void {
  }

  public prepared(): void {
    if (this.that.ui.danmuModel) {
      this.that.ui.danmuModel.start(this.that.ui.playbackPositionMs);
      this.that.ui.enableDanmu = true
    }
  }
}
