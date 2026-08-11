import { BaseUIState } from '../../../common/base/BaseUIState'
import { MineHistoryItem } from './MineHistoryPlanner'

export enum MineHistoryPageState {
  Loading,
  Content,
  Empty,
  Error
}

@ObservedV2
export class MineHistoryPageUIState extends BaseUIState {
  @Trace historyState: MineHistoryPageState = MineHistoryPageState.Loading
  @Trace items: MineHistoryItem[] = []
  @Trace loadingMore: boolean = false
  @Trace loadMoreFailed: boolean = false
  @Trace hasMore: boolean = true
}
