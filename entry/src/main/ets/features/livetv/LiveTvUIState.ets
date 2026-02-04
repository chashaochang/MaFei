import { BaseUIState } from "../../common/base/BaseUIState";
import { PlayerStatus, PlayStatus } from "../player/PlayStatus";

@ObservedV2
export class LiveTvUIState extends BaseUIState {

  @Trace url?: string
  @Trace isLoading: boolean = true
  @Trace playerStatus: number = PlayerStatus.IDLE
  @Trace CONTROL_PlayStatus: number = PlayStatus.INIT;
  //正在投屏
  @Trace isCastPlaying: boolean = false
  //显示频道信息展示层
  @Trace showChannelInfo: boolean = false
}