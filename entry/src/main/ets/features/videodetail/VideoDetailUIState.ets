import { BaseItemPerson, ExternalUrl } from "sdk/src/main/ets/generated-client/models"
import { BaseUIState } from "../../common/base/BaseUIState"
import { LastSpanAttribute, TextSectionAttribute } from "../../component/textexpand/Model"
import { VideoItem } from "../home/hometab/HomeUIState"

@ObservedV2
export class VideoDetailUIState extends BaseUIState {
  @Trace id: string = ''
  @Trace title: string = '视频详情'
  @Trace mediaName: string = ''
  @Trace subtitle: string = ''
  @Trace isTvShows: boolean = false
  @Trace isMovie: boolean = false
  @Trace image: string = ''
  @Trace logo: string = ''
  @Trace episodeName : string = ''
  @Trace score: number = 0
  @Trace info: string = ''
  @Trace overview: string = ''
  @Trace people: BaseItemPerson[] = []
  //标签
  @Trace tags: string[] = []
  @Trace showActionBar = false
  @Trace path: string = ''
  @Trace size: string = ''
  @Trace createDate: string = ''
  @Trace videoInfo: string = ''
  @Trace audioInfo: string = ''
  @Trace subtitleInfo: string = ''
  @Trace externalUrls: ExternalUrl[] = []

  @Trace seasons: VideoItem[] = []

  scroller: Scroller = new Scroller()
  @Trace blur: number = 0
  @Trace actionBarOpacity: number = 0
  @Trace textSectionAttribute?: TextSectionAttribute

  @Trace percentageWatched: number = 0
  @Trace playbackPositionTicks: number = 0
  //剩余时间
  @Trace remainingTime: string = ''
  @Trace playButtonText: string = '播放'
}

export type VideoTag = '1080' | '4K' | 'STEREO' | 'HDR' | 'SDR' | 'DTS' | 'DOLBY_VISION' | 'DOLBY_ATOMS'
  | 'DOLBY_SURROUND'