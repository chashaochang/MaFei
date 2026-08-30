import { HMRouter, HMRouterMgr } from "@hadss/hmrouter"

import { PlayOptions } from "../../player/interaction/PlayOptions"
import { PlayerException } from "../../player/PlayerException"
import { display, window } from "@kit.ArkUI"
import { Constants } from "../../utils/Constants"
import { common } from "@kit.AbilityKit"
import { emitter } from "@kit.BasicServicesKit"
import { MediaPlaybackSource } from "../../media/playback/MediaPlaybackModels"
import { avSession } from "@kit.AVSessionKit"
import { MediaStreamUtil } from "../../utils/MediaStreamUtil"
import { PlayerMenus } from "../../player/ui/PlayerMenus"
import { OneMoreUtil } from "../../utils/OneMoreUtil"
import { PlayerLifecycle } from "../../router/lifecycles/PlayerLifecycle"
import { ToastUtil } from "../../utils/ToastUtil"
import { PlayerPageUIState } from "./PlayerPageUIState"
import { PlayerPageViewModel } from "./PlayerPageViewModel"
import { MFPlayerView } from "./MFPlayerView"
import { RouterConsts } from "../../common/RouterConsts"
import { PlayerFullscreenHelper } from "../../player/ui/PlayerFullscreenHelper"
import { WindowUtil } from "../../utils/WindowUtil"
import { AppRouteDestination, AppRouteDestinationOptions } from "../../component/AppRouteDestination"
import { AppPreference } from '../../data/AppPreference'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct PlayerPageContent {
  @Param videoUrl: string = ''
  @Event onBack: () => void = () => {}
  @Event onFullscreenToggle: () => void = () => {}

  build() {
    Stack() {
      if (!this.videoUrl) {
        Stack({ alignContent: Alignment.Center }) {
          Image($r('app.media.icon_load'))
            .objectFit(ImageFit.Auto)
            .width('120px')
            .height('120px')
        }
        .width('100%')
        .height('100%')
      } else {
        MFPlayerView({
          videoUrl: this.videoUrl,
          onBack: (): void => this.onBack(),
          onFullscreenToggle: (): void => this.onFullscreenToggle()
        })
          .width('100%')
          .height('100%')
      }
    }
    .alignContent(Alignment.Top)
    .width('100%')
    .height('100%')
    .backgroundColor(Color.Black)
  }
}

@HMRouter({
  pageUrl: RouterConsts.PlayerPage,
  useNavDst: true,
  singleton: true,
  lifecycle: 'playerLifecycle',
})
@ComponentV2
export struct PlayerPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  options: PlayOptions = HMRouterMgr.getCurrentParam() as PlayOptions
  @Provider() ui: PlayerPageUIState = new PlayerPageUIState(this.getUIContext())
  @Provider() vm: PlayerPageViewModel = new PlayerPageViewModel(this.ui)
  castController: avSession.AVCastController | undefined = undefined;
  playerFullscreenHelper: PlayerFullscreenHelper = new PlayerFullscreenHelper()
  backClickListener: () => void = () => {
    HMRouterMgr.pop()
  }
  @Local lifecycle: PlayerLifecycle = HMRouterMgr.getCurrentLifecycleOwner()?.getLifecycle() as PlayerLifecycle;

  private restoreSystemBars(): void {
    this.vm.appUIState.windowClass?.setWindowSystemBarEnable(['status', 'navigation'])
  }

  private restorePlayerWindowState(): void {
    this.vm.cancelPendingPlayback()
    this.vm.appUIState.isFullScreen = false
    this.restoreSystemBars()
    if (OneMoreUtil.isNormalPhone() && canIUse('SystemCapability.Window.SessionManager')) {
      WindowUtil.setMainWindowPortrait(getContext(this))
    }
  }

  private enterFullscreen(): void {
    this.vm.appUIState.isFullScreen = true
    this.vm.appUIState.windowClass?.setWindowSystemBarEnable([])
    this.playerFullscreenHelper.enableFullscreen()
  }

  private toggleFullscreen(): void {
    if (this.vm.appUIState.isFullScreen) {
      this.vm.appUIState.isFullScreen = false
      this.restoreSystemBars()
      if (OneMoreUtil.shouldRequestPlayerManualRotation(this.vm.appUIState.windowClass)) {
        WindowUtil.setMainWindowPortrait(getContext(this))
      }
      return
    }
    this.enterFullscreen()
  }

  // @Monitor('lifecycle.options')
  // onNewInstance() {
  //   this.options = this.lifecycle.options
  //   this.ui = new PlayerPageUIState(this.getUIContext())
  //   this.vm = new PlayerPageViewModel(this.ui)
  //   this.aboutToAppear()
  // }

  private handleMediaSourceApplied(mediaSource: MediaPlaybackSource): void {
    const selectedSourceId = `${mediaSource.sourceInfo.Id || ''}`.trim()
    if (selectedSourceId.length > 0) {
      try {
        AppPreference.getInstance().setPreferredMediaSourceId(mediaSource.ref, selectedSourceId)
      } catch (_error) {
        // A storage failure must never interrupt an active playback session.
      }
    }
    if (MediaStreamUtil.isLandscape(mediaSource.selectedVideoStream)) {
      // 横向视频自动全屏必须同步切换窗口态，否则系统栏会残留。
      this.enterFullscreen()
    }
    // else if (appPreferences.exoPlayerStartLandscapeVideoInLandscape) {
    //   // Auto-switch to landscape for landscape videos if enabled
    //   requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    // }

    // Update title and player menus
    this.vm.ui.title = mediaSource.name
    if (OneMoreUtil.isPc()) {
      let windowStage = AppStorage.get<window.WindowStage>('playerWindowStage')
      if (canIUse('SystemCapability.Window.SessionManager')) {
        let title = "正在播放: " + mediaSource.name
        windowStage?.getMainWindowSync().setWindowTitle(title)
      }
    }
    this.vm.ui.playerMenus?.onQueueItemChanged(mediaSource, this.vm.queueManager.hasNext())
  }

  private async initializePlayback(): Promise<void> {
    const result = await this.vm.queueManager.initializePlaybackQueue(this.options)
    switch (result) {
      case PlayerException.InvalidPlayOptions:
        ToastUtil.showToast(this.getUIContext(), '无法解析播放信息')
        break
      case PlayerException.NetworkFailure:
        ToastUtil.showToast(this.getUIContext(), '无法从服务器加载媒体信息')
        break
      case PlayerException.UnsupportedContent:
        ToastUtil.showToast(this.getUIContext(), '该媒体无法在本设备上播放')
        break
      default:
        break
    }
  }

  aboutToAppear(): void {
    // if (OneMoreUtil.isPc()) {
    //   this.options = this.lifecycle.options
    // }
    if (OneMoreUtil.isBigScreen()) {
      if (this.vm.appUIState.orientation == display.Orientation.LANDSCAPE ||
        this.vm.appUIState.orientation == display.Orientation.LANDSCAPE_INVERTED) {
        //如果现在横屏分栏展示,需要手动点击才能全屏
        OneMoreUtil.updateNavModifier(this.vm.appUIState)
      }
    }
    this.vm.initAVSession()
    // let callback = async () => {
    //   let defaultDisplay = display.getDefaultDisplaySync()
    //   this.vm.appUIState.isFullScreen = defaultDisplay.width > defaultDisplay.height
    // }
    // try {
    //   display.on("change", callback); // 监听屏幕状态改变
    // } catch (exception) {
    //   console.error('PlayerScreen', 'Failed to register callback. Code: ' + JSON.stringify(exception));
    // }
    emitter.on(Constants.PLAYER_PAGE_EVENT, (data) => {
      if (data.data && data.data['eventName']) {
        let name = data.data['eventName'] as string
        switch (name) {
          case 'onBackPressed':
            this.onBackPress()
            break
        }
      }
    })
    // Create playback menus
    this.vm.ui.playerMenus = new PlayerMenus() //(this, playerBinding, playerControlsBinding)
    this.vm.setMediaSourceAppliedCallback((source: MediaPlaybackSource): void => {
      this.handleMediaSourceApplied(source)
    })
    this.initializePlayback()
  }

  aboutToDisappear() {
    this.vm.setMediaSourceAppliedCallback(undefined)
    this.restorePlayerWindowState()
    this.vm.reportPlaybackStop()
    emitter.off(Constants.PLAYER_PAGE_EVENT.eventId)
    //后台播放音频
    // if (!this.viewModel.notificationHelper.allowBackgroundAudio) {
    //   this.viewModel.pause()
    // }
    // this.viewModel.playerOrNull?.stop()
    // this.viewModel.playerOrNull?.release()
    let context = getContext(this) as common.UIAbilityContext;
    context.eventHub.off('onForeground')
    context.eventHub.off('onBackground')
    this.vm.release()
  }

  onBackPress(): boolean | void {
    if (this.ui.playerMenuVisible) {
      this.ui.playerMenuDismissRequest += 1
      return true
    }
    if (this.vm.appUIState.isFullScreen) {
      this.toggleFullscreen()
      return true
    }
    this.restorePlayerWindowState()
    this.vm.reportPlaybackStop()
    HMRouterMgr.pop()
  }

  private handleNativeNavigationBack(): boolean {
    this.onBackPress()
    return true
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: '',
        titleBarVisible: false,
        contentExtendsUnderTitleBar: true,
        heroTitleChrome: false,
        titleMaterialFollowsSystem: false,
        beforeBack: () => this.handleNativeNavigationBack()
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            PlayerPageContent({
              videoUrl: this.ui.url,
              onBack: (): void => {
                this.onBackPress()
              },
              onFullscreenToggle: (): void => this.toggleFullscreen()
            })
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        PlayerPageContent({
          videoUrl: this.ui.url,
          onBack: (): void => {
            this.onBackPress()
          },
          onFullscreenToggle: (): void => this.toggleFullscreen()
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
