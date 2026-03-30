import { getHlsSegmentApi, getLibraryApi, getPlaystateApi, getSubtitleApi, ImageUrlsApi } from "sdk";
import { ApiClient } from "../../network/ApiClient";
import { danDanPlayConfig } from "../../config/api-config";
import { BusinessError, emitter } from "@kit.BasicServicesKit";
import { JellyfinMediaSource } from "../../player/source/JellyfinMediaSource";
import {
  BaseItemDtoQueryResult,
  BaseItemKind,
  ItemFields,
  MediaStream,
  PlayMethod,
  RepeatMode
} from "sdk/src/main/ets/generated-client/models";
import { Constants } from "../../utils/Constants";
import axios, { AxiosError, AxiosResponse } from "@ohos/axios";
import { buffer, uri } from "@kit.ArkTS";
import { SubtitleManager } from "../../utils/SubtitleManager";
import { IjkMediaPlayer } from "@ohos/ijkplayer";
import { QueueItem } from "../../entity/QueueItem";
import avSessionManager from "../../utils/AVSessionManager";
import { avSession } from "@kit.AVSessionKit";
import qualityOptionsProvider from "../../player/qualityoptions/QualityOptionsProvider";
import { BackgroundTaskManager } from "../../utils/BackgroundTaskManager";
import { SimilarItem } from "../../entity/SimilarItem";
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
import { PlayStatus } from "./PlayStatus";
import { TrackSelectionHelper } from "../../player/TrackSelectionHelper";
import { parseXmlToCommentData } from "./danmu/DanmuUtil";
import { CommentData, CommentResponseV2 } from "./danmu/CommentResponseV2";
import { DanDanPlayParser } from "./danmu/DandanPlayParser";
import { SearchBangumiResponse } from "./danmu/SearchBangumiResponse";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { AppPreference } from "../../data/AppPreference";
import { PlayerEngineType } from "../../entity/PlayerEngineType";

@ObservedV2
export class PlayerPageViewModel extends BaseViewModel<PlayerPageUIState>{
  queueManager: QueueManager = new QueueManager(this)
  subtitleManager: SubtitleManager = new SubtitleManager()
  ui: PlayerPageUIState
  danmuContext: DanmakuContext

  constructor(ui: PlayerPageUIState) {
    super(ui)
    this.ui = ui
    this.danmuContext = DanmakuContext.create();
    this.danmuInit()
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

  private isMpvEngine(): boolean {
    return AppPreference.getInstance().getPlayerEngineType() === PlayerEngineType.MPV
  }

  @Monitor('mediaSourceOrNull')
  onMediaSourceChanged() {
    let source = this.mediaSourceOrNull
    if (source) {
      console.warn(`[PlayerVM] source changed engine=${this.isMpvEngine() ? 'mpv' : 'other'}, subtitleCount=${source.subtitleStreams.length}, selectedSubtitle=${source.selectedSubtitleStream?.Index ?? 'none'}, defaultSubtitle=${source.sourceInfo.DefaultSubtitleStreamIndex ?? 'null'}, videoRange=${source.selectedVideoRangeType}, dvProfile=${source.selectedVideoDvProfile ?? 'none'}, hdrStrategy=${source.getVideoHdrStrategyLabel()}`)
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
      if (!this.isMpvEngine()) {
        this.searchDanmuku()
      }
    }
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

  get mediaSourceOrNull(): JellyfinMediaSource | undefined {
    return this.queueManager.currentMediaSourceOrNull
  }

  trackSelectionHelper: TrackSelectionHelper = new TrackSelectionHelper(this)
  initialTracksSelected: boolean = false
  //由于这个player要设置完url才能得到,暂时保存初始化信息
  initSource?: JellyfinMediaSource

  load(source: JellyfinMediaSource, url: string[], playWhenReady: boolean) {
    this.initSource = source
    this.ui.selectedBitrate = source.maxStreamingBitrate
    this.ui.mediaType = source.item.Type!
    this.ui.currentPlayId = this.queueManager.currentQueue[this.queueManager.currentQueueIndex]
    for (let index = 0; index < url.length; index++) {
      let element = url[index];
      //适配小雅
      if (element.includes('xiaoya.host')) {
        let URI = new uri.URI(ApiClient.Instance().basePath)
        element = element.replace('http://xiaoya.host', `${URI.scheme}:${URI.host}`)
      }
      if (index === 0) {
        this.ui.url = element
        //设置播放器url,开始播放
        this.ui.playWhenReady = playWhenReady
        this.initialTracksSelected = false
        let startTime = this.initSource.startTimeMs
        if (startTime > 0) {
          this.ui.initSeek = startTime
        }
        if (source.item.Type == BaseItemKind.Episode) { //电视剧
          this.getItems()
          avSessionManager.getSession()?.setAVQueueTitle(source.item.SeriesName ?? source.item.Name ?? "TITLE",)
        }
        avSessionManager?.getSession()?.setAVMetadata(source.toMediaMetadata())
        this.reportPlaybackStart(source)
      } else {
        console.log('外挂字幕:' + element);
      }
    }
  }

  stopTranscoding(mediaSource: JellyfinMediaSource) {
    if (mediaSource.playMethod == PlayMethod.Transcode) {
      getHlsSegmentApi(ApiClient.Instance()).stopEncodingProcess({
        deviceId: ApiClient.Instance().deviceInfo.id,
        playSessionId: mediaSource.playSessionId,
      })
    }
  }

  reportPlaybackStart(mediaSource: JellyfinMediaSource) {
    getPlaystateApi(ApiClient.Instance()).reportPlaybackStart({
      playbackStartInfo: {
        ItemId: mediaSource.itemId,
        PlayMethod: mediaSource.playMethod,
        PlaySessionId: mediaSource.playSessionId,
        AudioStreamIndex: mediaSource.selectedAudioStream?.Index,
        SubtitleStreamIndex: mediaSource.selectedSubtitleStream?.Index,
        // IsPaused:true,
        IsMuted: false,
        CanSeek: true,
        PositionTicks: mediaSource.startTimeMs * Constants.TICKS_PER_MILLISECOND,
        // VolumeLevel:audio.getAudioManager().getVolume(audio.AudioVolumeType.MEDIA),
        RepeatMode: RepeatMode.RepeatNone
      }
    })
  }

  async reportPlaybackStop() {
    let mediaSource = this.mediaSourceOrNull
    if (!mediaSource) {
      return
    }
    let hasFinished = this.ui.CONTROL_PlayStatus == PlayStatus.DONE
    let lastPositionTicks =
      hasFinished ? mediaSource.runTimeTicks :
        (this.isMpvEngine() ? this.ui.playbackPositionMs : IjkMediaPlayer.getInstance().getCurrentPosition()) * Constants.TICKS_PER_MILLISECOND
    let playStateApi = getPlaystateApi(ApiClient.Instance())
    await playStateApi.reportPlaybackStopped({
      playbackStopInfo: {
        ItemId: mediaSource.itemId,
        PositionTicks: lastPositionTicks,
        PlaySessionId: mediaSource.playSessionId,
        LiveStreamId: mediaSource.liveStreamId,
        Failed: false
      }
    })
    // Mark video as watched if playback finished
    if (hasFinished) {
      playStateApi.markPlayedItem({
        itemId: mediaSource.itemId
      })
    }
    // Stop active encoding if transcoding
    this.stopTranscoding(mediaSource)
  }

  selectSubtitle(selectedSubtitleStream: MediaStream) {
    getSubtitleApi(ApiClient.Instance()).getSubtitle({
      routeItemId: this.initSource!.itemId,
      routeMediaSourceId: this.initSource?.id,
      routeIndex: selectedSubtitleStream.Index,
      routeFormat: selectedSubtitleStream?.Codec
    }).then((res: AxiosResponse<string, ArrayBuffer>) => {
      let resText = buffer.from(res.data).toString()
      this.ui.subtitles = this.subtitleManager.parse(resText, selectedSubtitleStream!.Codec!)
    }).catch((e: BusinessError) => {
      console.error(e.message)
    })
  }

  getSubtitle(source: JellyfinMediaSource) {
    if (this.isMpvEngine()) {
      this.ui.currentSubtitle = ''
      this.ui.subtitles = []
      return
    }
    if (source.playMethod == PlayMethod.Transcode) {
      this.ui.currentSubtitle = ''
      this.ui.subtitles = []
      return
    }
    if (source.selectedSubtitleStream) {
      getSubtitleApi(ApiClient.Instance()).getSubtitle({
        routeItemId: source.itemId,
        routeMediaSourceId: source.id,
        routeIndex: source.selectedSubtitleStreamIndex,
        routeFormat: source.selectedSubtitleStream.Codec
      }).then((res: AxiosResponse<string, ArrayBuffer>) => {
        let resText = buffer.from(res.data).toString()
        this.ui.subtitles = this.subtitleManager.parse(resText, source.selectedSubtitleStream!.Codec!)
      }).catch((e: BusinessError) => {
        console.error(e.message)
      })
    }
  }

  //获取播放列表信息
  getItems() {
      this.queueManager.getItems().then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
        console.log(this.initSource?.itemId)
        console.log(JSON.stringify(res.data.Items));
        let items = res.data.Items
        if (this.queueManager.currentQueue.length > 0) {
          let list: QueueItem[] = []
          let sessionQueue: avSession.AVQueueItem[] = []
          this.queueManager.currentQueue.forEach(e => {
            let item = items?.find(i => i.Id == e)
            if (!item) {
              return
            }
            let queueItem: QueueItem = {
              id: e,
              playIndex: item?.IndexNumber ?? NaN,
              name: item?.Name ?? ''
            }
            list.push(queueItem)
            let image =
              ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(item.SeriesId ?? item.Id)
            let queueItemDescription: avSession.AVMediaDescription = {
              assetId: item.Id!,
              title: item.SeriesName ?? item.Name ?? "TITLE",
              subtitle: item.SeriesName ? (item.Name ?? '') : '',
              mediaImage: image,
            };
            let avQueueItem: avSession.AVQueueItem = {
              itemId: item.IndexNumber!,
              description: queueItemDescription
            };
            sessionQueue.push(avQueueItem)
          })
          this.ui.playList = list
          avSessionManager.getSession()?.setAVQueueItems(sessionQueue)
          this.ui.sessionQueue = sessionQueue
        }
        this.ui.scroller.scrollToIndex(this.ui.playList.findIndex(e => e.id == this.ui.currentPlayId))
      }).catch((e: BusinessError) => {

      })

  }

  release() {
    avSessionManager.unregisterSessionListener()
    //BackgroundTaskManager.stopContinuousTask(this.context);
  }

  getPositionAndPause(): number {
    emitter.emit(Constants.PLAYER_EVENT, {
      data: {
        'eventName': 'Pause'
      }
    })
    if (this.isMpvEngine()) {
      return this.ui.playbackPositionMs
    }
    return IjkMediaPlayer.getInstance().getCurrentPosition()
  }

  changeBitrate(bitrate?: number) {
    this.queueManager.changeBitrate(bitrate)
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
        startPosition: IjkMediaPlayer.getInstance().getCurrentPosition(),
        // 投播资源播放时长，设置该字段可将本机播放时长同步到远端显示
        duration: IjkMediaPlayer.getInstance().getDuration(),
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
    let queueItem = this.ui.playList.find(e => e.id == this.ui.currentPlayId)
    let image =
      ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(queueItem?.id)
    let avQueueItem: avSession.AVQueueItem = {
      itemId: queueItem!.playIndex,
      description: {
        assetId: queueItem!.id,
        title: (this.ui.mediaType == BaseItemKind.Episode ? this.ui.seriesName : this.ui.title) ?? '',
        subtitle: (this.ui.mediaType == BaseItemKind.Episode ? this.ui.title : '') ?? '',
        mediaImage: image,
        mediaUri: this.ui.url,
        mediaType: 'VIDEO',
        appName: Constants.APP_INFO_NAME,
        //startPosition为投播当前进度，设置该字段可将本机播放进度同步到远端
        startPosition: 0,
        // 投播资源播放时长，设置该字段可将本机播放时长同步到远端显示
        duration: IjkMediaPlayer.getInstance().getDuration(),
      }
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
    this.queueManager.next()
  };
  public playPreviousCallback = (): void => {
    this.queueManager.previous()
  };

  changeEpisode(index: number) {
    this.queueManager.changeEpisode(index)
  }

  async searchDanmuku() {
    let that = this
    //先从弹幕插件api获取
    let id = this.mediaSourceOrNull!.itemId
    try {
      let urlRes: AxiosResponse<object> = await axios.get<object>(ApiClient.Instance().basePath + '/api/danmu/' + id)
      let url = urlRes.data['url'] as string
      if (url && url.length > 0) {
        let xmlRes: AxiosResponse<string> = await axios.get<string>(url)
        if (xmlRes.data.length > 0) {
          let comments: CommentData[] = parseXmlToCommentData(xmlRes.data)
          if (this.ui.danmuModel != null) {
            let parser: BaseDanmakuParser = new DanDanPlayParser()
            let jsonSource = new JSONSource(JSON.stringify(comments));
            parser.load(jsonSource);
            this.ui.danmuModel.prepare(parser, this.danmuContext);
            this.ui.danmuModel.setCallback(new Call(that));
            this.onPlayStateChanged()
            return
          }
        }
      }
    } catch (e) {
      console.log(e)
    }
    //插件api没有的从弹弹PlayApi获取
    let anime = encodeURIComponent(this.mediaSourceOrNull!.item.SeriesName!)
    let episode = this.mediaSourceOrNull!.item.IndexNumber
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
      this.that.ui.danmuModel.start(IjkMediaPlayer.getInstance().getCurrentPosition());
      this.that.ui.enableDanmu = true
    }
  }
}
