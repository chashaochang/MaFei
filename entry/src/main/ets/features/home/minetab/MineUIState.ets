import { BaseUIState } from "../../../common/base/BaseUIState"
import { MineHistoryItem } from "./MineHistoryPlanner"

export enum MineHistoryState {
  Loading,
  Content,
  Empty,
  Error
}

@ObservedV2
export class MineUIState extends BaseUIState {
  @Trace
  userName: string = '未登录'
  @Trace
  serverName: string = ''
  @Trace
  serverVersion: string = ''
  @Trace
  userAvatar: string = ''
  @Trace
  userPolicy: string = ''
  @Trace isAdministrator: boolean = false
  @Trace managementAvailable: boolean = true
  @Trace webITongAvailable: boolean = true
  @Trace versionName: string = ''
  @Trace historyState: MineHistoryState = MineHistoryState.Loading
  @Trace historyItems: MineHistoryItem[] = []
}
