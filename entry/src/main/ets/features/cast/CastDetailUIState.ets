import { BaseItemDto } from "sdk/src/main/ets/generated-client/models";
import { BaseUIState } from "../../common/base/BaseUIState";

@ObservedV2
export class CastDetailUIState extends BaseUIState {

  personId:string = '';
  @Trace name: string = '';
  @Trace image: string = '';
  @Trace overview: string = '';
  @Trace sortName: string = '';
  @Trace isShowOverviewSheet: boolean = false;
  @Trace isFavorite: boolean = false
  @Trace movieCount: number = 0
  @Trace movies: BaseItemDto[] = []
  @Trace episodeCount: number = 0
  @Trace episodes: BaseItemDto[] = []
  @Trace seriesCount: number = 0
  @Trace series: BaseItemDto[] = []
}