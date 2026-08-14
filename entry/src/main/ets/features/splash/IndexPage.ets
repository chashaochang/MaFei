import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
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
import { MediaLibraryRefreshEvent, TokenExpiredEvent } from "../../events/Events";
import { AppRouteDestination } from "../../component/AppRouteDestination";
import { ManagementLibraryRepository } from "../management/library/ManagementLibraryRepository";
import { ManagementLibraryOperationResult } from "../management/library/ManagementLibraryModels";
import { MusicHomeLifecycle } from "../music/MusicHostLifecycle";

@HMRouter({
  pageUrl: RouterConsts.IndexPage,
  singleton: true,
  useNavDst: true,
  lifecycle: 'musicHomeLifecycle'
})
@ComponentV2
export struct IndexPage {
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  apiClientController: ApiClientController = new ApiClientController()
  private readonly homeScroller: Scroller = new Scroller()
  private readonly chasingScroller: Scroller = new Scroller()
  private readonly favoriteScroller: Scroller = new Scroller()
  private readonly mediaScroller: Scroller = new Scroller()
  private readonly mineScroller: Scroller = new Scroller()
  private readonly libraryRepository: ManagementLibraryRepository = new ManagementLibraryRepository()
  //是否点了取消登录
  @Local isCancel: boolean = false
  private musicLifecycle?: MusicHomeLifecycle =
    HMRouterMgr.getCurrentLifecycleOwner()?.getLifecycle() as MusicHomeLifecycle

  aboutToAppear(): void {
    emitter.on(TokenExpiredEvent, () => {
      this.handleTokenExpired().catch((): void => {})
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
              await this.apiClientController.setupServer(serverEntity!.hostname, serverEntity?.servername,
                serverEntity?.version, successState.serverId)
              await this.apiClientController.setupUser(serverEntity!.id, userId, token)
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

  private async handleTokenExpired(): Promise<void> {
    try {
      this.getUIContext().getPromptAction().showToast({
        message: "AccessToken已失效,请重新登录"
      })
    } catch (_error) {
    }
    await this.apiClientController.logout()
    this.appUIState.isLogin = false
  }

  private async cancelConnection(): Promise<void> {
    this.isCancel = true
    await this.apiClientController.logout()
    this.appUIState.isConnecting = false
  }

  private rootNavigationHeroChromeVisible(): boolean {
    return this.appUIState.rootNavigationHomeActionsVisible &&
      this.appUIState.rootNavigationHomeHeroVisible
  }

  private showLibraryOperationResult(result: ManagementLibraryOperationResult): void {
    this.getUIContext().getPromptAction().showToast({
      message: result.ok ? $r('app.string.management_library_scan_started') :
        $r('app.string.management_library_operation_failed')
    })
  }

  private async startAllLibraryScan(): Promise<void> {
    if (this.appUIState.rootNavigationMediaScanBusy) {
      return
    }
    this.appUIState.rootNavigationMediaScanBusy = true
    try {
      this.showLibraryOperationResult(await this.libraryRepository.startAllScan())
    } finally {
      this.appUIState.rootNavigationMediaScanBusy = false
    }
  }

  private mediaAdministratorMenus(): Array<NavigationMenuItem> {
    return [{
      value: $r('app.string.management_library_add'),
      icon: $r('sys.symbol.plus'),
      action: () => {
        HMRouterMgr.to(RouterConsts.ManagementLibraryEditorPage)
          .withParam({ libraryId: '' })
          .push()
      }
    }, {
      value: $r('app.string.management_library_scan_all'),
      icon: $r('sys.symbol.play_fill'),
      isEnabled: !this.appUIState.rootNavigationMediaScanBusy,
      action: () => {
        this.startAllLibraryScan()
      }
    }, {
      value: $r('app.string.management_library_title'),
      icon: $r('sys.symbol.list_bullet_square'),
      action: () => {
        HMRouterMgr.to(RouterConsts.ManagementLibrariesPage).push()
      }
    }, {
      value: $r('app.string.management_refresh'),
      icon: $r('sys.symbol.arrow_clockwise'),
      action: () => {
        this.getUIContext().getHostContext()?.eventHub.emit(MediaLibraryRefreshEvent)
      }
    }]
  }

  private rootNavigationMenus(): Array<NavigationMenuItem> {
    let menus: Array<NavigationMenuItem> = []
    if (this.appUIState.rootNavigationMediaAdminActionsVisible) {
      return this.mediaAdministratorMenus()
    }
    if (!this.appUIState.rootNavigationHomeActionsVisible) {
      return menus
    }
    if (this.appUIState.rootNavigationLiveTvAvailable) {
      menus.push({
        value: '电视直播',
        icon: $r('app.media.iptv'),
        action: () => {
          HMRouterMgr.to(RouterConsts.LiveTvPage).push()
        }
      })
    }
    menus.push({
      value: '搜索',
      icon: $r('sys.symbol.magnifyingglass'),
      action: () => {
        HMRouterMgr.to(RouterConsts.SearchPage).push()
      }
    })
    return menus
  }

  private rootNavigationScrollControllers(): Array<Scroller> {
    return [
      this.homeScroller,
      this.chasingScroller,
      this.favoriteScroller,
      this.mediaScroller,
      this.mineScroller
    ]
  }

  @Builder
  private pageContent() {
    Stack() {
      if (this.appUIState.isLogin) {
        HomeScreen({
          homeScroller: this.homeScroller,
          chasingScroller: this.chasingScroller,
          favoriteScroller: this.favoriteScroller,
          mediaScroller: this.mediaScroller,
          mineScroller: this.mineScroller
        })
      } else {
        ConnectScreen()
        ConnectingScreen({
          onLogoutClick: () => {
            this.cancelConnection().catch((): void => {
              this.appUIState.isConnecting = false
            })
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

  build() {
    AppRouteDestination({
      title: this.appUIState.rootNavigationTitle,
      titleBarVisible: this.appUIState.isLogin && this.appUIState.rootNavigationTitleBarVisible,
      backButtonVisible: false,
      contentExtendsUnderTitleBar: this.appUIState.rootNavigationTitleBarVisible,
      heroTitleChrome: this.rootNavigationHeroChromeVisible(),
      titleMaterialFollowsSystem: true,
      ownsRootNavigationChrome: true,
      menus: this.rootNavigationMenus(),
      scrollControllers: this.rootNavigationScrollControllers(),
      contentBuilder: () => {
        this.pageContent()
      },
      legacyContentBuilder: () => {
        this.pageContent()
      }
    })
  }
}
