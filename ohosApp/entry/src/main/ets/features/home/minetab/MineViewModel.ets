import { AxiosResponse } from "@ohos/axios";
import { ImageUrlsApi } from "sdk";
import { UserDto } from "sdk/src/main/ets/generated-client/models";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { BaseViewModel } from "../../../common/base/BaseViewModel";
import { ApiClient } from "../../../network/ApiClient";
import { MineUIState } from "./MineUIState";
import { bundleManager, common } from "@kit.AbilityKit";
import { ApiClientController } from "../../../data/ApiClientController";
import { CustomColorMode } from "../../../entity/CustomColorMode";

export class MineViewModel extends BaseViewModel<MineUIState> {

  init() {
    this.getAppVersion()
    getUserApi(ApiClient.Instance()).getUserById({
      userId: ApiClient.Instance().userId
    }).then((res: AxiosResponse<UserDto>) => {
      if (res.data) {
        let user = res.data
        this.ui.userName = user.Name || ''
        this.ui.userAvatar =
          ApiClient.Instance().basePath + new ImageUrlsApi().getUserImageUrl({
            Id: ApiClient.Instance().userId
          }, {
            tag: user.PrimaryImageTag,
            quality: 90
          })
        this.ui.userPolicy = user.Policy?.IsAdministrator ? '管理员' : '普通用户'
      }
    }).catch()
  }

  private getAppVersion() {
    try {
      let context = getContext(this) as common.UIAbilityContext;
      let bundleName = context.applicationInfo.name;

      bundleManager.getBundleInfoForSelf(bundleManager.BundleFlag.GET_BUNDLE_INFO_WITH_APPLICATION)
        .then((bundleInfo) => {
          this.ui.versionName = `v${bundleInfo.versionName}`;
        })
        .catch(() => {
          console.error('Failed to get bundle info');
        });
    } catch (error) {
      console.error('Failed to get context:', error);
    }
  }

  getColorModeName() {
    if (this.appUIState.customColorMode == CustomColorMode.Auto) {
      this.ui.colorModeName = $r('app.string.color_mode_auto')
    } else if (this.appUIState.customColorMode == CustomColorMode.Light) {
      this.ui.colorModeName = $r('app.string.color_mode_light')
    } else if (this.appUIState.customColorMode == CustomColorMode.Dark) {
      this.ui.colorModeName = $r('app.string.color_mode_dark')
    }
  }

  logout() {
    new ApiClientController().logout()
    this.appUIState.isLogin = false
  }
}