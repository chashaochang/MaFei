import { BaseItemPerson } from "sdk/src/main/ets/generated-client/models"
import { BaseUIState } from "../../common/base/BaseUIState"
import { VideoItem } from "../home/hometab/HomeUIState"

@ObservedV2
export class SeasonDetailUIState extends BaseUIState {
  @Trace id = ''
  @Trace parentId = ''
  @Trace title: string = '视频详情'
  @Trace mediaName: string = ''
  @Trace backgroundImage: string = ''
  @Trace image: string = ''
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
  @Trace people: BaseItemPerson[] = []
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
  @Trace hasEdgeEffect: boolean = false;
  @Trace currentScrollState: ScrollState = ScrollState.Idle;
  @Trace imageHeight: number = SeasonDetailUIState.imageInitHeight;
  @Trace firstLoading: boolean = true;
}