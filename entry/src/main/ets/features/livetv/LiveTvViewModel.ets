import { AxiosResponse } from "@ohos/axios";
import { getLiveTvApi } from "sdk";
import { BaseItemDto, BaseItemDtoQueryResult, ItemFields } from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { ApiClient } from "../../network/ApiClient";
import { LiveTvUIState } from "./LiveTvUIState";
import { QueueManager } from "./QueueManager";
import { PlayerException } from "../../player/PlayerException";
import { ToastUtil } from "../../utils/ToastUtil";
import { PlayOptions } from "../../player/interaction/PlayOptions";
import { getItemImageUrl } from "../../common/CommonFunc";
import { MediaPlaybackPreparedStreams, MediaPlaybackSource } from '../../media/playback/MediaPlaybackModels'

@ObservedV2
export class LiveTvViewModel extends BaseViewModel<LiveTvUIState> {

  queueManager: QueueManager = new QueueManager(this)
  @Trace channelList: BaseItemDto[] = []
  @Trace playIndex: number = 0
  //由于这个player要设置完url才能得到,暂时保存初始化信息
  initSource?: MediaPlaybackSource
  private channelInfoTimer?: number // 频道信息显示定时器

  getChannelList() {
    getLiveTvApi(ApiClient.Instance()).getLiveTvChannels({
      startIndex: 0,
      limit: 100,
      fields: [ItemFields.PrimaryImageAspectRatio],
      userId: ApiClient.Instance().userId,
    }).then((res:AxiosResponse<BaseItemDtoQueryResult>)=>{
      if (res.data && res.data.Items && res.data.Items.length > 0) {
        this.channelList = res.data.Items
        this.play()
      } else {

      }
    }).catch(()=>{

    })
  }

  play() {
    switch (this.queueManager.initializePlaybackQueue(new Object({
      ids: this.channelList.map(e => e.Id!),
      startIndex: this.playIndex,
      mediaSourceId: null,
      startPositionTicks: 0,
      audioStreamIndex: null,
      subtitleStreamIndex: null,
    }) as PlayOptions)) {
      case PlayerException.InvalidPlayOptions:
        ToastUtil.showToast(this.ui.context, '无法解析播放信息')
        break
      case PlayerException.NetworkFailure:
        ToastUtil.showToast(this.ui.context, '无法从服务器加载媒体信息')
        break
      case PlayerException.UnsupportedContent:
        ToastUtil.showToast(this.ui.context, '该媒体无法在本设备上播放')
        break
      default:
        break
    }
  }

  load(source: MediaPlaybackSource, streams: MediaPlaybackPreparedStreams, playWhenReady: boolean) {
    this.initSource = source
    this.ui.videoHeaders = streams.videoHeaders.slice()
    this.ui.url = streams.videoUrl
  }

  playNext(){
    if (this.playIndex < this.channelList.length - 1) {
      this.playIndex++
    }
    this.queueManager.next()
    this.showChannelInfoWithDelay()
  }

  playPrevious(){
    if (this.playIndex > 0) {
      this.playIndex--
    }
    this.queueManager.previous()
    this.showChannelInfoWithDelay()
  }

  /**
   * 获取当前频道的图片URL
   */
  getCurrentChannelImageUrl(): string {
    if (this.channelList.length > 0 && this.playIndex < this.channelList.length) {
      return getItemImageUrl(this.channelList[this.playIndex])
    }
    return ''
  }

  /**
   * 显示频道信息，3秒后自动隐藏
   */
  showChannelInfoWithDelay() {
    // 清除之前的定时器
    if (this.channelInfoTimer) {
      clearTimeout(this.channelInfoTimer)
    }

    // 显示频道信息
    this.ui.showChannelInfo = true

    // 3秒后隐藏
    this.channelInfoTimer = setTimeout(() => {
      this.ui.showChannelInfo = false
    }, 3000)
  }
}
