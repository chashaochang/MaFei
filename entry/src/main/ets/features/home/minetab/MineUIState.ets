import { BaseUIState } from "../../../common/base/BaseUIState"

@ObservedV2
export class MineUIState extends BaseUIState {
  @Trace
  userName: string = '未登录'
  @Trace
  userAvatar: string = ''
  @Trace
  userPolicy: string = ''
  @Trace versionName: string = ''
}