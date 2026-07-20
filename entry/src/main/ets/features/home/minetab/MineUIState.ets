import { BaseUIState } from "../../../common/base/BaseUIState"

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
  @Trace versionName: string = ''
  @Trace appearanceName: string = ''
  @Trace playerEngineName: ResourceStr = $r('app.string.player_engine_auto')
}
