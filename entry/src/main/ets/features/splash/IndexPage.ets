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
import {
  MediaProviderComposition,
  MediaProviderRestoreStatus
} from '../setting/account/MediaProviderComposition';
import { MediaProviderKind } from '../../media/MediaProviderKind';
import { MediaProviderRegistry } from '../../media/MediaProviderRegistry';
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../theme/AppThemeSurfaceResolver';

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
  @Local restoreBusy: boolean = false
  @Local feiniuRestoreConnectionError: boolean = false
  private tokenExpiredSubscribed: boolean = false
  private musicLifecycle?: MusicHomeLifecycle =
    HMRouterMgr.getCurrentLifecycleOwner()?.getLifecycle() as MusicHomeLifecycle

  aboutToAppear(): void {
    this.restoreSelectedAccount()
      .catch((): void => {
        this.appUIState.isConnecting = false
        this.appUIState.isLogin = false
      })
      .finally((): void => {
        this.syncTokenExpiredSubscription()
      })
  }

  aboutToDisappear(): void {
    if (this.tokenExpiredSubscribed) {
      emitter.off(TokenExpiredEvent)
      this.tokenExpiredSubscribed = false
    }
  }

  private syncTokenExpiredSubscription(): void {
    const activeSession = MediaProviderRegistry.instance().getActiveSession()
    const shouldSubscribe = activeSession?.provider === MediaProviderKind.Jellyfin
    if (shouldSubscribe && !this.tokenExpiredSubscribed) {
      emitter.on(TokenExpiredEvent, () => {
        this.handleTokenExpired().catch((): void => {})
      })
      this.tokenExpiredSubscribed = true
    } else if (!shouldSubscribe && this.tokenExpiredSubscribed) {
      emitter.off(TokenExpiredEvent)
      this.tokenExpiredSubscribed = false
    }
  }

  private async handleTokenExpired(): Promise<void> {
    const hostContext = this.getUIContext().getHostContext()
    if (hostContext) {
      const composition = await MediaProviderComposition.initialize(hostContext)
      if (composition.selectedProvider() === MediaProviderKind.FeiniuVideo) {
        return
      }
    }
    try {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.jellyfin_session_expired')
      })
    } catch (_error) {
    }
    await this.apiClientController.logout()
    this.appUIState.isLogin = false
  }

  private async cancelConnection(): Promise<void> {
    this.isCancel = true
    const hostContext = this.getUIContext().getHostContext()
    if (hostContext) {
      const composition = await MediaProviderComposition.initialize(hostContext)
      if (composition.selectedProvider() === MediaProviderKind.FeiniuVideo) {
        await composition.clearSelectedFeiniu()
        this.feiniuRestoreConnectionError = false
        this.appUIState.isLogin = false
        this.appUIState.isConnecting = false
        return
      }
    }
    await this.apiClientController.logout()
    this.appUIState.isConnecting = false
  }

  private async restoreSelectedAccount(): Promise<void> {
    if (this.restoreBusy) {
      return
    }
    const hostContext = this.getUIContext().getHostContext()
    if (!hostContext) {
      return
    }
    this.restoreBusy = true
    this.isCancel = false
    try {
      const composition = await MediaProviderComposition.initialize(hostContext)
      if (composition.hasActiveSelectedSession()) {
        this.feiniuRestoreConnectionError = false
        this.appUIState.isLogin = true
        this.appUIState.isConnecting = false
        return
      }
      const selectedProvider = composition.selectedProvider()
      if (selectedProvider === MediaProviderKind.FeiniuVideo) {
        await this.restoreSelectedFeiniu(composition)
        return
      }
      this.feiniuRestoreConnectionError = false
      if (selectedProvider === MediaProviderKind.Jellyfin ||
        composition.shouldRestoreLegacyJellyfin() || this.appUIState.isLogin) {
        await this.restoreLegacyJellyfin(composition)
      } else {
        this.appUIState.isLogin = false
        this.appUIState.isConnecting = false
      }
    } finally {
      this.restoreBusy = false
    }
  }

  private async restoreSelectedFeiniu(composition: MediaProviderComposition): Promise<void> {
    this.appUIState.isConnecting = true
    const status = await composition.restoreSelectedFeiniu()
    if (this.isCancel) {
      await composition.clearSelectedFeiniu()
      this.appUIState.isLogin = false
      this.appUIState.isConnecting = false
      return
    }
    if (status === MediaProviderRestoreStatus.Restored) {
      this.feiniuRestoreConnectionError = false
      this.appUIState.isLogin = true
    } else if (status === MediaProviderRestoreStatus.AuthenticationExpired) {
      this.feiniuRestoreConnectionError = false
      this.appUIState.isLogin = false
      this.showToast($r('app.string.feiniu_video_session_expired'))
    } else if (status === MediaProviderRestoreStatus.RetryableFailure) {
      this.feiniuRestoreConnectionError = true
      this.appUIState.isLogin = false
    } else {
      this.feiniuRestoreConnectionError = false
      this.appUIState.isLogin = false
    }
    this.appUIState.isConnecting = false
  }

  private async restoreLegacyJellyfin(composition: MediaProviderComposition): Promise<void> {
    const token = AppPreference.getInstance().getAccessToken()
    const userId = AppPreference.getInstance().getCurrentUserId()
    if (token.length === 0 || userId.length === 0) {
      this.appUIState.isLogin = false
      this.appUIState.isConnecting = false
      return
    }
    const serverEntity = this.apiClientController.loadSavedServer()
    if (!serverEntity) {
      this.appUIState.isLogin = false
      this.appUIState.isConnecting = false
      return
    }
    this.appUIState.isConnecting = true
    try {
      const state = await connectionHelper.checkServerUrl(serverEntity.hostname)
      if (this.isCancel) {
        return
      }
      if (state.type !== CheckUrlStateType.Success) {
        this.appUIState.isLogin = false
        return
      }
      const successState = state as Success
      await this.apiClientController.setupServer(
        serverEntity.hostname,
        serverEntity.servername,
        serverEntity.version,
        successState.serverId
      )
      await this.apiClientController.setupUser(serverEntity.id, userId, token)
      if (serverEntity.servername === '' || serverEntity.version === '') {
        try {
          const response: AxiosResponse<PublicSystemInfo> =
            await getSystemApi(ApiClient.Instance()).getPublicSystemInfo()
          if (response.data !== undefined) {
            serverEntity.servername = response.data.ServerName || ''
            serverEntity.version = response.data.Version || ''
          }
        } catch (_error) {
        }
        this.apiClientController.updateServerName(serverEntity)
      }
      this.appUIState.isLogin = await composition.mirrorLegacyJellyfinSession()
    } catch (_error) {
      this.appUIState.isLogin = false
    } finally {
      this.appUIState.isConnecting = false
    }
  }

  private showToast(message: ResourceStr): void {
    try {
      this.getUIContext().getPromptAction().showToast({ message: message })
    } catch (_error) {
    }
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle,
      this.appUIState.nativeThemeAvailable
    )
  }

  @Builder
  private feiniuRestoreErrorContent() {
    Column({ space: 14 }) {
      Image($r('app.media.feiniu_video_logo'))
        .width(88)
        .height(88)
        .objectFit(ImageFit.Contain)
      Text($r('app.string.feiniu_video_restore_failed_title'))
        .fontSize(20)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.useNativeSurface() ?
          $r('sys.color.font_primary') : $r('app.color.text_primary'))
      Text($r('app.string.feiniu_video_restore_connection_error'))
        .fontSize(14)
        .fontColor(this.useNativeSurface() ?
          $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .textAlign(TextAlign.Center)
        .margin({ left: 28, right: 28 })
      Button($r('app.string.common_retry'))
        .width(220)
        .height(48)
        .fontColor(Color.White)
        .backgroundColor($r('app.color.color_main'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.PrimaryInteractive,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick((): void => {
          this.restoreSelectedAccount().catch((): void => {
            this.appUIState.isConnecting = false
          })
        })
      Button($r('app.string.account_choose_other'))
        .width(220)
        .height(44)
        .backgroundColor(Color.Transparent)
        .fontColor(this.useNativeSurface() ?
          $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveInteractive,
          this.useNativeSurface(),
          this.appUIState.systemMaterialAvailable
        ))
        .onClick((): void => {
          HMRouterMgr.to(RouterConsts.AccountPage).withParam({ fromLogin: true }).push()
        })
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
    .backgroundColor(this.useNativeSurface() ?
      $r('app.color.native_canvas_background') : $r('app.color.bg_main'))
  }

  private rootNavigationHeroChromeVisible(): boolean {
    return this.appUIState.rootNavigationHomeActionsVisible &&
      this.appUIState.rootNavigationHomeHeroVisible
  }

  private providerSupportsManagement(): boolean {
    const session = MediaProviderRegistry.instance().getActiveSession()
    return session === undefined || session.capabilities.management
  }

  private providerSupportsLiveTv(): boolean {
    const session = MediaProviderRegistry.instance().getActiveSession()
    return session === undefined || session.capabilities.liveTv
  }

  private providerSupportsSearch(): boolean {
    const session = MediaProviderRegistry.instance().getActiveSession()
    return session === undefined || session.capabilities.search
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
    if (this.appUIState.rootNavigationMediaAdminActionsVisible &&
      this.providerSupportsManagement()) {
      return this.mediaAdministratorMenus()
    }
    if (!this.appUIState.rootNavigationHomeActionsVisible) {
      return menus
    }
    if (this.appUIState.rootNavigationLiveTvAvailable && this.providerSupportsLiveTv()) {
      menus.push({
        value: '电视直播',
        icon: $r('app.media.iptv'),
        action: () => {
          HMRouterMgr.to(RouterConsts.LiveTvPage).push()
        }
      })
    }
    if (this.providerSupportsSearch()) {
      menus.push({
        value: '搜索',
        icon: $r('sys.symbol.magnifyingglass'),
        action: () => {
          HMRouterMgr.to(RouterConsts.SearchPage).push()
        }
      })
    }
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
      } else if (this.feiniuRestoreConnectionError) {
        this.feiniuRestoreErrorContent()
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
