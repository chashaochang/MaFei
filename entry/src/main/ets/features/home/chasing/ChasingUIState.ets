import { BaseUIState } from "../../../common/base/BaseUIState"
import { VideoItem } from "../hometab/HomeUIState"

@ObservedV2
export class ChasingUIState extends BaseUIState {
  @Trace summary: string = '暂无追剧内容'
  @Trace totalCount: number = 0
  @Trace followedCount: number = 0
  @Trace continueCount: number = 0
  @Trace nextUpCount: number = 0
  @Trace sections: ChaseSection[] = []
  @Trace isRefreshing: boolean = false
  @Trace refreshStatus: RefreshStatus = RefreshStatus.Inactive
  @Trace refreshOffset: number = 0
}

export interface ChaseSection {
  kind: string
  title: string
  subtitle: string
  items: VideoItem[]
}

export interface ChasePlanResult {
  ok: boolean
  summary: string
  totalCount: number
  sections: ChaseSection[]
}
