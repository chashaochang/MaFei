import { HMRouter } from "@hadss/hmrouter";
import { RouterConsts } from "../../common/RouterConsts";
import { AppUIState } from "../../entity/AppUIState";
import { AppStorageV2 } from "@kit.ArkUI";
import { HomeScreen } from "../home/HomeScreen";
import { ConnectingScreen } from "../connect/ConnectingScreen";
import { ConnectScreen } from "../connect/ConnectScreen";
import { ApiClientController } from "../../data/ApiClientController";
import { AppPreference } from "../../data/AppPreference";
import connectionHelper from "../../setup/ConnectionHelper";
import { CheckUrlStateType, Success } from "../../ui/state/CheckUrlState";
import { PublicSystemInfo } from "sdk";
import { AxiosResponse } from "@ohos/axios";
import { getSystemApi } from "sdk/src/main/ets/utils/api/system-api";
import { ApiClient } from "../../network/ApiClient";
import { emitter } from "@kit.BasicServicesKit";
import { TokenExpiredEvent } from "../../events/Events";

@HMRouter({
  pageUrl: RouterConsts.IndexPage,
  singleton: true
})
@ComponentV2
export struct IndexPage {
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  apiClientController: ApiClientController = new ApiClientController()
  //是否点了取消登录
  @Local isCancel: boolean = false

  aboutToAppear(): void {
    emitter.on(TokenExpiredEvent, () => {
      //401
      try {
        this.getUIContext().getPromptAction().showToast({
          message: "AccessToken已失效,请重新登录"
        })
      } catch (error) {

      }
      this.apiClientController.logout()
      this.appUIState.isLogin = false
    })
    //token是永久有效的,只要有token就认为是登录状态
    let token = AppPreference.getInstance().getAccessToken()
    let userId = AppPreference.getInstance().getCurrentUserId()
    let isLogin = token.length > 0 && userId.length > 0
    if (isLogin) {
      let serverEntity = this.apiClientController.loadSavedServer()
      if (serverEntity != null) {
        this.appUIState.isConnecting = true
        connectionHelper.checkServerUrl(serverEntity.hostname)
          .then(async (state) => {
            if (this.isCancel) {
              return
            }
            if (state.type === CheckUrlStateType.Success) {
              const successState = state as Success
              this.apiClientController.setupServer(serverEntity!.hostname, serverEntity?.servername,
                serverEntity?.version, successState.serverId)
              this.apiClientController.setupUser(serverEntity!.id, userId, token)
              //数据库升级version1新增字段处理
              if (serverEntity?.servername === '' || serverEntity?.version === '') {
                try {
                  let res: AxiosResponse<PublicSystemInfo> =
                    await getSystemApi(ApiClient.Instance()).getPublicSystemInfo()
                  if (res.data != undefined) {
                    serverEntity!.servername = res.data.ServerName || ''
                    serverEntity!.version = res.data.Version || ''
                  }
                } catch (e) {
                }
                this.apiClientController.updateServerName(serverEntity!)
              }
              this.appUIState.isLogin = true
            } else {
              this.appUIState.isLogin = false
            }
            this.appUIState.isConnecting = false
          })
          .catch(() => {
            this.appUIState.isConnecting = false
          })
      }
    }
  }

  aboutToDisappear(): void {
    emitter.off(TokenExpiredEvent)
  }

  build() {
    Stack() {
      if (this.appUIState.isLogin) {
        HomeScreen()
      } else {
        if (!this.appUIState.isConnecting) {
          ConnectScreen()
        }
        ConnectingScreen({
          onLogoutClick: () => {
            this.isCancel = true
            this.apiClientController.logout()
            this.appUIState.isConnecting = false
          }
        })
          .visibility(this.appUIState.isConnecting ? Visibility.Visible : Visibility.Hidden)
          .transition(TransitionEffect.asymmetric(
            TransitionEffect.OPACITY.animation({ duration: 0 }),

            // 弹性缩小到中心点
            TransitionEffect.scale({ x: 0, y: 0 })
              .animation({
                duration: 600,
                curve: Curve.Linear
              })
              .combine(TransitionEffect.OPACITY
                .animation({ duration: 400, delay: 100 }))
          ))
      }
    }.width('100%').height('100%')
  }
}
