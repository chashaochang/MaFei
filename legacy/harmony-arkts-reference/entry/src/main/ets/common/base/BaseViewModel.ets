import { AppUIState } from "../../entity/AppUIState";
import { BaseUIState } from "./BaseUIState"
import { AppStorageV2 } from "@kit.ArkUI";

@ObservedV2
export class BaseViewModel<T extends BaseUIState> {
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  ui: T

  constructor(ui: T) {
    this.ui = ui
  }

  postEvent(eventName: string, ...params: Object[]): void {
    this.ui.context.getHostContext()?.eventHub.emit(eventName, params)
  }
}