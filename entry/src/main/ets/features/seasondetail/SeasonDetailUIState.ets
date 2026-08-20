import { BaseUIState } from "../../common/base/BaseUIState"
import { MediaImageRef, MediaPersonCredit } from "../../media/catalog/MediaCatalogModels"
import { MediaRef } from "../../media/MediaRef"
import { VideoItem } from "../home/hometab/HomeUIState"

@ObservedV2
export class SeasonDetailUIState extends BaseUIState {
  @Trace id = ''
  @Trace parentId = ''
  mediaRef?: MediaRef
  seriesRef?: MediaRef
  @Trace title: string = '视频详情'
  @Trace mediaName: string = ''
  @Trace backgroundImage: string = ''
  @Trace backgroundImageRef?: MediaImageRef
  @Trace image: string = ''
  @Trace imageRef?: MediaImageRef
  @Trace episodeName : string = ''
  //剧名
  @Trace seriesName = ''
  //第几季
  @Trace seasonName = ''
  @Trace score: number = 0
  //从上一页带进来的信息,在这里取不到
  preInfo: string = ''
  @Trace info: string = ''
  @Trace overview: string = ''
  @Trace people: MediaPersonCredit[] = []
  @Trace isFavorite: boolean = false
  @Trace played: boolean = false

  @Trace showActionBar = false

  @Trace seasons: VideoItem[] = []
  @Trace selectGroupIndex: number = 0
  @Trace videoGroups: string[] = []
  @Trace videos: VideoItem[][] = []

  scroller: Scroller = new Scroller()
  videoScroller: Scroller = new Scroller()
  @Trace blur: number = 0
  @Trace actionBarOpacity: number = 0

  static readonly imageInitHeight: number = 320;
  static readonly nativeImageInitHeight: number = 430;
  @Trace imageBaseHeight: number = SeasonDetailUIState.imageInitHeight;
  @Trace hasEdgeEffect: boolean = false;
  @Trace currentScrollState: ScrollState = ScrollState.Idle;
  @Trace imageHeight: number = SeasonDetailUIState.imageInitHeight;
  @Trace firstLoading: boolean = true;
}
