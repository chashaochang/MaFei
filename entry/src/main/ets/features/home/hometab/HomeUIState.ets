import { BaseItemKind, CollectionType } from "sdk/src/main/ets/generated-client/models"
import { BaseUIState } from "../../../common/base/BaseUIState"

export const HOME_RECENT_MEDIA_ID: string = '__home_recent__'

@ObservedV2
export class HomeUIState extends BaseUIState{
  @Trace initialLoading: boolean = true
  //媒体库列表
  @Trace mediaList: MediaItem[] = []
  //继续观看列表
  @Trace resumeList: VideoItem[] = []
  //首页推荐轮播
  @Trace recommendationList: VideoItem[] = []
  //接下啦列表
  @Trace nextUpList: VideoItem[] = []
  //媒体库列表
  @Trace latestMediaList: MediaItem[] = []
  @Trace latestMap: Map<string, VideoItem[]> = new Map
  @Trace selectedLatestMediaId: string = HOME_RECENT_MEDIA_ID
  @Trace latestMediaLoading: boolean = true
  @Trace latestMediaLoadingMore: boolean = false
  @Trace latestMediaHasMore: boolean = true
  @Trace latestMediaLoadFailed: boolean = false

  @Trace isRefreshing: boolean = false;
  @Trace refreshStatus: RefreshStatus = RefreshStatus.Inactive
  @Trace refreshOffset: number = 0

  @Trace totalCount: string = '--'
  @Trace movieCount: string = '--'
  @Trace tvCount: string = '--'

  @Trace hasLiveTv = false
}

export interface MediaItem {
  name: string,
  image: ResourceStr,
  id: string,
  childCount: number,
  type?: BaseItemKind,
  collectionType?: CollectionType,
}

export interface VideoItem {
  id: string,
  name: string,
  image: string,
  seriesId?: string,
  mediaName?: string,
  type?: string,
  percent?: number,
  subtitle?: string,
  description?: string,
  score?: number,//评分
  tags?: string[],//都有什么分辨率的
  time?: string,//时长
}
