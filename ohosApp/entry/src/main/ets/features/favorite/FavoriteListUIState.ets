import { BaseUIState } from "../../common/base/BaseUIState";

@ObservedV2
export class FavoriteListUIState extends BaseUIState {

  @Trace title: string = '收藏'
  @Trace isRefreshing: boolean = false;
}