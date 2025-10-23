import { AxiosResponse } from "@ohos/axios";
import { getTvShowsApi, getUserLibraryApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemFields
} from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl, getTagBase64, getVideoTagsByStreams, playItems } from "../../common/CommonFunc";
import { ApiClient } from "../../network/ApiClient";
import { VideoItem } from "../home/hometab/HomeUIState";
import { VideoDetailUIState } from "./VideoDetailUIState";
import { common, Want } from "@kit.AbilityKit";
import { curves } from '@kit.ArkUI';

export class VideoDetailViewModel extends BaseViewModel<VideoDetailUIState> {
  private originImageHeight: number = 0;
  private springBackAnimation: curves.ICurve = curves.interpolatingSpring(0, 1, 288, 30);
  episodeList: string[] = []

  getVideoDetail(id: string) {
    getUserLibraryApi(ApiClient.Instance()).getItem({
      userId: ApiClient.Instance().userId,
      itemId: id,
    }).then((res: AxiosResponse<BaseItemDto>) => {
      if (res.data) {
        let itemData = res.data
        this.ui.title = itemData.Name || ''
        this.ui.image = getItemImageUrl(itemData, ImageType.Backdrop)
        this.ui.logo = getItemImageUrl(itemData, ImageType.Logo)
        this.ui.score = itemData.CommunityRating ? itemData.CommunityRating : 0

        if (itemData.Type == BaseItemKind.Episode) {
          this.ui.subtitle = `${itemData.SeriesName} ${itemData.SeasonName} · ${itemData.IndexNumber}集`
          this.ui.info = ` ${itemData.ProductionYear}  /  ${itemData.RunTimeTicks ?
            Math.floor(itemData.RunTimeTicks / 10000000 / 60 / 60) + '小时' +
            Math.floor(itemData.RunTimeTicks / 10000000 / 60 % 60) + '分钟' : ''}`
        } else {
          this.ui.info =
            `${itemData.OfficialRating ? '/  ' + itemData.OfficialRating + '  ' :
              ''}/  ${itemData.ProductionYear}  /  ${itemData.Genres?.join('  ')}\n${itemData.RunTimeTicks ?
              Math.floor(itemData.RunTimeTicks / 10000000 / 60 / 60) + '小时' +
              Math.floor(itemData.RunTimeTicks / 10000000 / 60 % 60) + '分钟' : ''}  /  ${this.ui.mediaName}`
        }
        this.ui.overview = itemData.Overview || ''
        this.ui.people = itemData.People || []
        this.ui.tags = getVideoTagsByStreams(itemData.MediaStreams).map((e) => {
          return getTagBase64(e)
        }).filter((e) => {
          return e !== ''
        })
        this.ui.path = itemData.Path || ''
        this.ui.size = itemData.MediaSources && itemData.MediaSources.length > 0 && itemData.MediaSources[0].Size ?
          (itemData.MediaSources[0].Size! / 1024 /
        1024 / 1024
        )
        .
        toFixed(2) + ' GB' :
        ''
        this.ui.createDate = itemData.DateCreated ? itemData.DateCreated.split('T')[0] : ''
        //1080p H264 7 mbps · 8 bit
        this.ui.videoInfo = itemData.MediaStreams && itemData.MediaStreams.length > 0 ?
          itemData.MediaStreams.filter((e) => {
            return e.Type === 'Video'
          }).map((e) => {
            return `${e.DisplayTitle} ${e.BitRate ? Math.floor(e.BitRate / 1000000) + ' mbps' :
              ''} · ${e.BitDepth ? e.BitDepth + ' bit' : ''}`
          }).join('\n') : ''
        //英语 AAC · 48000 Hz
        this.ui.audioInfo = itemData.MediaStreams && itemData.MediaStreams.length > 0 ?
          itemData.MediaStreams.filter((e) => {
            return e.Type === 'Audio'
          }).map((e) => {
            return `${e.DisplayTitle} · ${e.SampleRate ?
              e.SampleRate + ' Hz' : ''}`
          }).join('\n') : ''
        //法语 SUBRIP
        this.ui.subtitleInfo = itemData.MediaStreams && itemData.MediaStreams.length > 0 ?
          itemData.MediaStreams.filter((e) => {
            return e.Type === 'Subtitle'
          }).map((e) => {
            return `${e.Language} ${e.Codec}`
          }).join('\n') : ''
        this.ui.externalUrls = itemData.ExternalUrls || []
        if (res.data.UserData?.PlaybackPositionTicks && res.data.UserData?.PlaybackPositionTicks > 0) {
          this.ui.playbackPositionTicks = res.data.UserData.PlaybackPositionTicks
          this.ui.percentageWatched = res.data.UserData.PlayedPercentage || 0
          //剩余 1 小时 19 分钟
          this.ui.remainingTime = "剩余 " +
          Math.floor(((itemData.RunTimeTicks! - res.data.UserData.PlaybackPositionTicks) / 10000000) / 60 / 60) +
            " 小时 " +
          Math.floor(((itemData.RunTimeTicks! - res.data.UserData.PlaybackPositionTicks) / 10000000) / 60 % 60) + " 分钟"
          this.ui.playButtonText = '继续播放'
        }
      }
    }).catch()
  }

  getTvShowDetail(id: string) {
    getUserLibraryApi(ApiClient.Instance()).getItem({
      userId: ApiClient.Instance().userId,
      itemId: id,
    }).then((res: AxiosResponse<BaseItemDto>) => {
      if (res.data) {
        let itemData = res.data
        this.ui.title = itemData.Name || ''
        this.ui.image = getItemImageUrl(itemData, ImageType.Backdrop)
        this.ui.logo = getItemImageUrl(itemData, ImageType.Logo)
        this.ui.info = `${itemData.Genres?.join('  ')}  /  ${this.ui.mediaName}`
        this.ui.overview = itemData.Overview || ''
        this.ui.people = itemData.People || []
        getTvShowsApi(ApiClient.Instance()).getSeasons({
          userId: ApiClient.Instance().userId,
          seriesId: id,
          fields: [ItemFields.ItemCounts, ItemFields.PrimaryImageAspectRatio, ItemFields.CanDelete,
            ItemFields.MediaSourceCount, ItemFields.ChildCount],
        }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
          if (res.data && res.data.Items && res.data.Items.length > 0) {
            this.ui.seasons = res.data.Items.map<VideoItem>((e) => {
              return {
                id: e.Id || '',
                name: e.Name || '',
                image: getItemImageUrl(e),
                subtitle: `共${e.ChildCount}集${e.ProductionYear != undefined ? ' · ' + e.ProductionYear : ''}`,
                score: e.CommunityRating ? e.CommunityRating : 0,
                tags: getVideoTagsByStreams(e.MediaStreams),
                mediaName: this.ui.title,
                type: e.Type,
              }
            })
          }
        }).catch()
      }
    }).catch()
  }

  getNextUp() {
    getTvShowsApi(ApiClient.Instance()).getNextUp({
      userId: ApiClient.Instance().userId,
      seriesId: this.ui.id,
      fields: ['MediaSourceCount']
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (res.data && res.data.Items && res.data.Items.length > 0) {
        let nextUp = res.data.Items[0]
        this.ui.episodeName = `${nextUp.SeasonName} 第 ${nextUp.IndexNumber} 集`
        this.ui.playButtonText = this.ui.episodeName
        this.episodeList = [nextUp.Id!]
      } else {
        this.getEpisodes()
      }
    }).catch()
  }

  getEpisodes() {
    getTvShowsApi(ApiClient.Instance()).getEpisodes({
      userId: ApiClient.Instance().userId,
      fields: [ItemFields.Chapters, ItemFields.Trickplay],
      isMissing: false,
      seriesId: this.ui.id,
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (res.data && res.data.Items && res.data.Items.length > 0) {
        let firstEpisode = res.data.Items[0]
        let seasonName = ''
        if ((firstEpisode.SeasonName || '').startsWith('第') && (firstEpisode.SeasonName || '').endsWith('季')) {
          //seasonName去掉第几季的空格并且在第后面和季前面加上空格
          seasonName = firstEpisode.SeasonName!.replace(/\s+/g, '')
          seasonName = seasonName.replace('第', '第 ').replace('季', ' 季')
        }
        this.ui.episodeName = (seasonName != '' ? seasonName + '  ' : '') + '第 ' + res.data.Items[0].IndexNumber + ' 集'
        this.ui.playButtonText = this.ui.episodeName
        this.episodeList = res.data.Items.map<string>((e) => e.Id!)
      }
    }).catch()
  }

  playFirst() {
    if (this.ui.episodeName == '') {
      return
    }
    playItems(this.episodeList, getContext())
  }

  openExternalUrl(url: string) {
    if (url.length > 0) {
      try {
        let context = this.ui.context.getHostContext() as common.UIAbilityContext;
        let want: Want = {
          action: 'ohos.want.action.viewData',
          entities: ['entity.system.browsable'],
          uri: url
        };
        context.startAbility(want).then(() => {
          console.info('Open URL success');
        }).catch(() => {
          console.error('Open URL failed');
        });
      } catch (error) {
        console.error('Failed to open URL:', error);
      }
    }
  }

  calculateImageHeight(param: CalculateHeightParam): boolean {
    if (param.yOffset === 0) {
      this.ui.hasEdgeEffect = false;
    } else {
      this.ui.hasEdgeEffect = param.offset > 0;
    }
    if (param.yOffset > 0) {
      return false;
    }
    if (param.state === ScrollState.Scroll && param.offset !== 0) {
      this.ui.currentScrollState = ScrollState.Scroll;
      const currentBannerHeight: number = this.ui.imageHeight - param.offset;
      if (currentBannerHeight < this.originImageHeight) {
        this.ui.currentScrollState = ScrollState.Fling;
        this.ui.imageHeight = this.originImageHeight;
        return false;
      }
      const bannerOffset: number = -(param.offset / BANNER_SCALE_FACTOR);
      this.ui.imageHeight += bannerOffset
      return true;
    } else if (this.ui.currentScrollState === ScrollState.Scroll && param.state === ScrollState.Fling) {
      this.bannerSpringBack();
      return true;
    }
    return false;
  }

  bannerSpringBack() {
    this.ui.currentScrollState = 2;
    this.ui.context.animateTo({ curve: this.springBackAnimation, duration: 250 }, () => {
      this.changeImageHeight();
    });
  }

  protected changeImageHeight(): void {
    this.ui.imageHeight = VideoDetailUIState.imageInitHeight
    this.originImageHeight = this.ui.imageHeight;
  }
}
// Banner magnification effect factor.
export const BANNER_SCALE_FACTOR: number = 3.00;
export interface CalculateHeightParam {
  yOffset: number;
  offset: number;
  state: ScrollState;
}