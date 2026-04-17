import { AxiosResponse } from "@ohos/axios";
import { getPlaystateApi, getTvShowsApi, getUserLibraryApi, ImageUrlsApi } from "sdk";
import {
  BaseItemDto,
  BaseItemDtoQueryResult,
  BaseItemKind,
  ImageType,
  ItemFields
} from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl, getTagBase64, getVideoTagsByStreams, openUrlByBrowser, playItems } from "../../common/CommonFunc";
import { ApiClient } from "../../network/ApiClient";
import { VideoItem } from "../home/hometab/HomeUIState";
import { VideoDetailUIState } from "./VideoDetailUIState";
import { common, Want } from "@kit.AbilityKit";
import { curves } from '@kit.ArkUI';
import { ToastUtil } from "../../utils/ToastUtil";
import { ReportService } from "../../network/ReportService";

export class VideoDetailViewModel extends BaseViewModel<VideoDetailUIState> {
  private static readonly CONTENT_REPORT_EMAIL: string = '1139396584@qq.com'
  private originImageHeight: number = 0;
  private springBackAnimation: curves.ICurve = curves.interpolatingSpring(0, 1, 288, 30);
  episodeList: string[] = []
  episodeStartIndex: number = 0

  private normalizeSeasonName(seasonName?: string | null): string {
    const value = (seasonName || '').trim()
    if (value.length === 0) {
      return ''
    }
    const compact = value.replace(/\s+/g, ' ').trim()
    if (/season\s*unknown/i.test(compact) || /unknown/i.test(compact)) {
      return ''
    }
    const englishSeason = compact.match(/^season\s*(\d+)$/i)
    if (englishSeason) {
      return `第 ${englishSeason[1]} 季`
    }
    if (/^specials?$/i.test(compact)) {
      return '特别篇'
    }
    if (compact.startsWith('第') && compact.endsWith('季')) {
      return compact.replace(/\s+/g, '').replace('第', '第 ').replace('季', ' 季')
    }
    return compact
  }

  private formatEpisodePlayText(item: BaseItemDto): string {
    const seasonName = this.normalizeSeasonName(item.SeasonName)
    return (seasonName !== '' ? seasonName + '  ' : '') + '第 ' + item.IndexNumber + ' 集'
  }

  private updateEpisodeQueue(items: BaseItemDto[], startIndex: number) {
    if (items.length === 0) {
      this.episodeList = []
      this.episodeStartIndex = 0
      return
    }
    this.episodeList = items.map<string>((e) => e.Id!)
    this.episodeStartIndex = Math.min(Math.max(startIndex, 0), this.episodeList.length - 1)
  }

  private loadEpisodeQueueForEpisode(itemData: BaseItemDto) {
    if (!itemData.SeriesId) {
      return
    }
    getTvShowsApi(ApiClient.Instance()).getEpisodes({
      userId: ApiClient.Instance().userId,
      fields: [ItemFields.Chapters, ItemFields.Trickplay],
      isMissing: false,
      seriesId: itemData.SeriesId,
      seasonId: itemData.SeasonId
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (res.data?.Items && res.data.Items.length > 0) {
        const episodeItems = res.data.Items
        const currentIndex = episodeItems.findIndex((episode) => episode.Id === itemData.Id)
        this.updateEpisodeQueue(episodeItems, currentIndex >= 0 ? currentIndex : 0)
      }
    }).catch()
  }

  hasEpisodeQueue(): boolean {
    return this.episodeList.length > 0
  }

  getVideoDetail(id: string) {
    getUserLibraryApi(ApiClient.Instance()).getItem({
      userId: ApiClient.Instance().userId,
      itemId: id,
    }).then((res: AxiosResponse<BaseItemDto>) => {
      if (res.data) {
        let itemData = res.data
        this.ui.title = itemData.Name || ''
        if (itemData.Type === BaseItemKind.Movie) {
          this.ui.isMovie = true
          this.ui.image =
            ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(itemData.Id, ImageType.Backdrop)
          this.ui.logo = getItemImageUrl(itemData, ImageType.Logo)
        } else {//电视剧
          this.ui.image =
            ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(itemData.Id, ImageType.Primary)
        }
        this.ui.score = itemData.CommunityRating ? itemData.CommunityRating : 0
        this.ui.isFavorite = itemData.UserData?.IsFavorite || false
        this.ui.played = itemData.UserData?.Played || false
        if (itemData.Type == BaseItemKind.Episode) {
          const normalizedSeasonName = this.normalizeSeasonName(itemData.SeasonName)
          this.ui.episodeName = this.formatEpisodePlayText(itemData)
          this.ui.subtitle =
            `${itemData.SeriesName || ''}${normalizedSeasonName ? ' ' + normalizedSeasonName : ''} · ${itemData.IndexNumber}集`
          this.ui.info = ` ${itemData.ProductionYear}  /  ${itemData.RunTimeTicks ?
            Math.floor(itemData.RunTimeTicks / 10000000 / 60 / 60) + '小时' +
            Math.floor(itemData.RunTimeTicks / 10000000 / 60 % 60) + '分钟' : ''}`
          this.loadEpisodeQueueForEpisode(itemData)
        } else {
          this.ui.info =
            `${itemData.OfficialRating ? '/  ' + itemData.OfficialRating + '  ' :
              ''}/  ${itemData.ProductionYear}  /  ${itemData.Genres?.join('  ')}\n${itemData.RunTimeTicks ?
              Math.floor(itemData.RunTimeTicks / 10000000 / 60 / 60) + '小时' +
              Math.floor(itemData.RunTimeTicks / 10000000 / 60 % 60) + '分钟' : ''}  /  ${this.ui.mediaName}`
        }
        this.ui.overview = itemData.Overview || ''
        this.ui.people = itemData.People || []
        this.ui.mediaSources = itemData.MediaSources || []
        this.getFileInfo()
        // this.ui.createDate = itemData.DateCreated ? itemData.DateCreated.split('T')[0] : ''
        this.getVideoInfo()
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
        this.ui.firstLoading = false
      }
    }).catch(() => {
      this.ui.firstLoading = false
    })
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
        //this.ui.people = itemData.People || []
        this.ui.isFavorite = itemData.UserData?.IsFavorite || false
        this.ui.played = itemData.UserData?.Played || false
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
            this.ui.firstLoading = false
          }
        }).catch(() => {
          this.ui.firstLoading = false
        })
      }
    }).catch(() => {
      this.ui.firstLoading = false
    })
  }

  getNextUp() {
    getTvShowsApi(ApiClient.Instance()).getNextUp({
      userId: ApiClient.Instance().userId,
      seriesId: this.ui.id,
      fields: ['MediaSourceCount']
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (res.data && res.data.Items && res.data.Items.length > 0) {
        let nextUp = res.data.Items[0]
        this.ui.episodeName = this.formatEpisodePlayText(nextUp)
        this.ui.playButtonText = this.ui.episodeName
        this.getEpisodes(nextUp.Id!)
      } else {
        this.getEpisodes()
      }
    }).catch()
  }

  getEpisodes(preferredEpisodeId?: string) {
    getTvShowsApi(ApiClient.Instance()).getEpisodes({
      userId: ApiClient.Instance().userId,
      fields: [ItemFields.Chapters, ItemFields.Trickplay],
      isMissing: false,
      seriesId: this.ui.id,
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (res.data && res.data.Items && res.data.Items.length > 0) {
        const episodeItems = res.data.Items
        const preferredIndex = preferredEpisodeId ?
          episodeItems.findIndex((episode) => episode.Id === preferredEpisodeId) : -1
        const startIndex = preferredIndex >= 0 ? preferredIndex : 0
        const targetEpisode = episodeItems[startIndex]
        this.ui.episodeName = this.formatEpisodePlayText(targetEpisode)
        this.ui.playButtonText = this.ui.episodeName
        this.updateEpisodeQueue(episodeItems, startIndex)
      }
    }).catch()
  }

  playFirst() {
    if (this.episodeList.length === 0) {
      return
    }
    playItems(this.episodeList, getContext(), undefined, undefined, this.episodeStartIndex)
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

  changeFavorite() {
    if (this.ui.isFavorite) {
      getUserLibraryApi(ApiClient.Instance()).unmarkFavoriteItem({
        userId: ApiClient.Instance().userId,
        itemId: this.ui.id,
      }).then(() => {
        this.ui.isFavorite = false
        ToastUtil.showToast(this.ui.context, '已取消收藏')
      }).catch(() => {
        ToastUtil.showToast(this.ui.context, '取消收藏失败')
      })
    } else {
      getUserLibraryApi(ApiClient.Instance()).markFavoriteItem({
        userId: ApiClient.Instance().userId,
        itemId: this.ui.id,
      }).then(() => {
        this.ui.isFavorite = true
        ToastUtil.showToast(this.ui.context, '已收藏')
      }).catch(() => {
        ToastUtil.showToast(this.ui.context, '收藏失败')
      })
    }
  }

  changePlayedState() {
    if (this.ui.played) {
      getPlaystateApi(ApiClient.Instance()).markUnplayedItem({
        userId: ApiClient.Instance().userId,
        itemId: this.ui.id,
      }).then(() => {
        this.ui.played = false
        ToastUtil.showToast(this.ui.context, '标记为未观看')
      }).catch(() => {
        ToastUtil.showToast(this.ui.context, '操作失败')
      })
    } else {
      getPlaystateApi(ApiClient.Instance()).markPlayedItem({
        userId: ApiClient.Instance().userId,
        itemId: this.ui.id,
      }).then(() => {
        this.ui.played = true
        ToastUtil.showToast(this.ui.context, '标记为已观看')
      }).catch(() => {
        ToastUtil.showToast(this.ui.context, '操作失败')
      })
    }
  }

  getTags(): string[] {
    this.ui.tags =
      getVideoTagsByStreams(this.ui.mediaSources[this.ui.selectedMediaSourceIndex].MediaStreams).map((e) => {
        return getTagBase64(e)
      }).filter((e) => {
        return e !== ''
      })
    return this.ui.tags;
  }

  getFileInfo() {
    let source = this.ui.mediaSources[this.ui.selectedMediaSourceIndex]
    this.ui.path = source.Path || ''
    this.ui.size = source.Size ? (source.Size! / 1024 /
    1024 / 1024
    )
    .
    toFixed(2) + ' GB' :
    ''
  }

  getVideoInfo() {
    let source = this.ui.mediaSources[this.ui.selectedMediaSourceIndex]

    //1080p H264 7 mbps · 8 bit
    this.ui.videoInfo = source.MediaStreams && source.MediaStreams.length > 0 ?
      source.MediaStreams.filter((e) => {
        return e.Type === 'Video'
      }).map((e) => {
        return `${e.DisplayTitle} ${e.BitRate ? Math.floor(e.BitRate / 1000000) + ' mbps' :
          ''} · ${e.BitDepth ? e.BitDepth + ' bit' : ''}`
      }).join('\n') : ''
    //英语 AAC · 48000 Hz
    this.ui.audioInfo = source.MediaStreams && source.MediaStreams.length > 0 ?
      source.MediaStreams.filter((e) => {
        return e.Type === 'Audio'
      }).map((e) => {
        return `${e.DisplayTitle} · ${e.SampleRate ?
          e.SampleRate + ' Hz' : ''}`
      }).join('\n') : ''
    //法语 SUBRIP
    this.ui.subtitleInfo = source.MediaStreams && source.MediaStreams.length > 0 ?
      source.MediaStreams.filter((e) => {
        return e.Type === 'Subtitle'
      }).map((e) => {
        return `${e.Language} ${e.Codec}`
      }).join('\n') : ''
  }
}

// Banner magnification effect factor.
export const BANNER_SCALE_FACTOR: number = 3.00;

export interface CalculateHeightParam {
  yOffset: number;
  offset: number;
  state: ScrollState;
}
