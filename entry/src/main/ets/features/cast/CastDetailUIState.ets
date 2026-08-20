import { BaseUIState } from "../../common/base/BaseUIState";
import { MediaImageRef, MediaSummary } from "../../media/catalog/MediaCatalogModels";
import { MediaRef } from "../../media/MediaRef";

@ObservedV2
export class CastDetailUIState extends BaseUIState {

  personId:string = '';
  mediaRef?: MediaRef
  @Trace name: string = '';
  @Trace image: string = '';
  @Trace imageRef?: MediaImageRef
  @Trace overview: string = '';
  @Trace sortName: string = '';
  @Trace isShowOverviewSheet: boolean = false;
  @Trace isFavorite: boolean = false
  @Trace movieCount: number = 0
  @Trace movies: MediaSummary[] = []
  @Trace episodeCount: number = 0
  @Trace episodes: MediaSummary[] = []
  @Trace seriesCount: number = 0
  @Trace series: MediaSummary[] = []
}
