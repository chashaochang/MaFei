import { BaseUIState } from "../../../common/base/BaseUIState";
import { MediaItem } from "../hometab/HomeUIState";

@ObservedV2
export class MediaUIState extends BaseUIState {
  //收藏数量
  @Trace favoriteCount: number = 0;
  //媒体库列表
  @Trace mediaList: MediaItem[] = []
  @Trace isRefreshing: boolean = false;
  @Trace refreshStatus: RefreshStatus = RefreshStatus.Inactive
  @Trace refreshOffset: number = 0
}
