import { AxiosResponse } from "@ohos/axios";
import { getTvShowsApi, getUserLibraryApi, ImageUrlsApi } from "sdk";
import { BaseItemDto, BaseItemDtoQueryResult, ImageType, ItemFields } from "sdk/src/main/ets/generated-client/models";
import { BaseViewModel } from "../../common/base/BaseViewModel";
import { getItemImageUrl, getVideoTagsByStreams, playItems } from "../../common/CommonFunc";
import { ApiClient } from "../../network/ApiClient";
import { VideoItem } from "../home/hometab/HomeUIState";
import { SeasonDetailUIState } from "./SeasonDetailUIState";

export class SeasonDetailViewModel extends BaseViewModel<SeasonDetailUIState> {
  episodeList: string[] = []

  // 将剧集按30个一组分组，并生成组名与视频列表
  private groupEpisodes(items: BaseItemDto[]) {
    // 先映射为 VideoItem 列表
    const mapped: VideoItem[] = items.map<VideoItem>((e: BaseItemDto) => {
      return {
        id: e.Id || '',
        name: `${e.IndexNumber}.${e.Name ?? ''}`.trim(),
        image: ApiClient.Instance().basePath + new ImageUrlsApi().getItemImageUrlById(e.Id, ImageType.Primary),
        subtitle: e.Overview || '',
        score: e.CommunityRating ? e.CommunityRating : 0,
        tags: getVideoTagsByStreams(e.MediaStreams),
        mediaName: this.ui.title,
        type: e.Type,
        time: `${e.RunTimeTicks ? (() => {
          const totalSeconds = Math.floor(e.RunTimeTicks / 10000000)
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
          if (seconds > 0) {
            parts.push(`${seconds}秒`)
          }
          // 如果小时和分钟都为0且秒也为0，返回空字符串；如果只有秒存在也显示秒
          return parts.length > 0 ? parts.join('') : '0秒'
        })() : ''}`,

      }
    })

    const groupSize = 30
    const total = mapped.length
    const groups: string[] = []
    const videos: VideoItem[][] = []

    for (let start = 0; start < total; start += groupSize) {
      const endExclusive = Math.min(start + groupSize, total)
      const slice = mapped.slice(start, endExclusive)
      const displayStart = start + 1
      const displayEnd = endExclusive
      let groupName: string
      if (slice.length === 1) {
        // 单独一集只显示单号，如 61
        groupName = `${displayStart}`
      } else {
        groupName = `${displayStart} - ${displayEnd}`
      }
      groups.push(groupName)
      videos.push(slice)
    }

    this.ui.videoGroups = groups
    this.ui.videos = videos
  }

  getSeasonDetail() {
    getUserLibraryApi(ApiClient.Instance()).getItem({
      userId: ApiClient.Instance().userId,
      itemId: this.ui.id,
    }).then((res: AxiosResponse<BaseItemDto>) => {
      if (res.data) {
        let itemData = res.data
        this.ui.title = `${itemData.SeriesName} ${itemData.Name}`
        this.ui.image = getItemImageUrl(itemData, ImageType.Primary)
        this.ui.backgroundImage = ApiClient.Instance().basePath +
        new ImageUrlsApi().getItemImageUrlById(itemData.ParentThumbItemId, ImageType.Backdrop)
        this.ui.seriesName = itemData.SeriesName || ''
        this.ui.seasonName = itemData.Name || ''
        this.ui.info = `${itemData.ProductionYear}  /  ${this.ui.preInfo}`
        this.ui.overview = itemData.Overview || ''
        this.ui.people = itemData.People || []
        getTvShowsApi(ApiClient.Instance()).getEpisodes({
          userId: ApiClient.Instance().userId,
          seasonId: this.ui.id,
          seriesId: itemData.SeriesId || '',
          fields: ['ItemCounts', 'PrimaryImageAspectRatio', 'CanDelete', 'MediaSourceCount', 'Overview'],
        }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
          if (res.data && res.data.Items && res.data.Items.length > 0) {
            // 分组剧集
            this.groupEpisodes(res.data.Items)
            this.getEpisodes(this.ui.id)
          } else {
            // 清空
            this.ui.videoGroups = []
            this.ui.videos = []
          }
        }).catch()
      }
    }).catch()
  }

  getEpisodes(seasonId: string) {
    getTvShowsApi(ApiClient.Instance()).getEpisodes({
      userId: ApiClient.Instance().userId,
      fields: [ItemFields.Chapters, ItemFields.Trickplay],
      isMissing: false,
      seriesId: this.ui.parentId,
      seasonId: seasonId
    }).then((res: AxiosResponse<BaseItemDtoQueryResult>) => {
      if (res.data && res.data.Items && res.data.Items.length > 0) {
        //遍历没播放的第一个视频
        let firstNotPlayed = res.data.Items.find((e) => e.UserData?.Played == false)
        if (firstNotPlayed) {
          this.ui.episodeName = '第 ' + firstNotPlayed.IndexNumber + ' 集'
          this.episodeList = [firstNotPlayed.Id!]
          return
        }
        let firstEpisode = res.data.Items[0]
        this.ui.episodeName = '第 ' + firstEpisode.IndexNumber + ' 集'
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
}