import { BaseUIState } from "../../common/base/BaseUIState";

@ObservedV2
export class WebITongUIState extends BaseUIState {
  @Trace pageTitle: string = "网(Web)易通"
  @Trace progress: number = 0
}