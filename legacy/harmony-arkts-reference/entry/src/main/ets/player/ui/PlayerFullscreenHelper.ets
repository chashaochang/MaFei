import { AppUIState } from "../../entity/AppUIState";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { AppStorageV2, window } from "@kit.ArkUI";

export class PlayerFullscreenHelper{

  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  enableFullscreen(){
    if(OneMoreUtil.isNormalPhone()){
      if (canIUse('SystemCapability.Window.SessionManager')) {
        // 全屏播放仅允许横屏，但必须保留正、反横屏之间的传感器旋转。
        this.appUIState.windowClass?.setPreferredOrientation(window.Orientation.AUTO_ROTATION_LANDSCAPE);
      }
    }
  }
}
