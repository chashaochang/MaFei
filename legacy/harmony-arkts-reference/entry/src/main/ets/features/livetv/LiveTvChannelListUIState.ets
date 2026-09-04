import { BaseUIState } from "../../common/base/BaseUIState"

@ObservedV2
export class LiveTvChannelListUIState extends BaseUIState {
  @Trace title: string = '视频列表'
  @Trace totalCount: string = '0'
  @Trace isDesc: boolean = true
  @Trace selectedSortIndex = 2
}