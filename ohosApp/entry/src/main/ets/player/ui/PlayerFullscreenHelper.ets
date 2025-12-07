import { AppUIState } from "../../entity/AppUIState";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { AppStorageV2, window } from "@kit.ArkUI";

export class PlayerFullscreenHelper{

  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  enableFullscreen(){
    if(OneMoreUtil.isNormalPhone()){
      if (canIUse('SystemCapability.Window.SessionManager')) {
        this.appUIState.windowClass?.setPreferredOrientation(window.Orientation.USER_ROTATION_LANDSCAPE);
      }
    }
  }
}