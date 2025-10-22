import { HMRouter, HMRouterMgr } from "@hadss/hmrouter"

import { PlayOptions } from "../../player/interaction/PlayOptions"
import { PlayerException } from "../../player/PlayerException"
import { AppStorageV2, display, window } from "@kit.ArkUI"
import { Constants } from "../../utils/Constants"
import { common } from "@kit.AbilityKit"
import { emitter } from "@kit.BasicServicesKit"
import { MediaStream } from "sdk/src/main/ets/generated-client/models"
import { QualityOption } from "../../player/qualityoptions/QualityOption"
import { avSession } from "@kit.AVSessionKit"
import { MediaStreamUtil } from "../../utils/MediaStreamUtil"
import { PlayerMenus } from "../../player/ui/PlayerMenus"
import { OneMoreUtil } from "../../utils/OneMoreUtil"
import { AppUIState } from "../../entity/AppUIState"
import { PlayerLifecycle } from "../../router/lifecycles/PlayerLifecycle"
import { ToastUtil } from "../../utils/ToastUtil"
import { PlayerUIState } from "./PlayerUIState"
import { PlayerViewModel } from "./PlayerViewModel"
import { PlayerView } from "./PlayerView"
import { RouterConsts } from "../../common/RouterConsts"

@HMRouter({
  pageUrl: RouterConsts.PlayerPage,
  singleton: true,
  lifecycle: 'playerLifecycle',
})
@ComponentV2
export struct PlayerPage {
  options: PlayOptions = HMRouterMgr.getCurrentParam() as PlayOptions
  @Provider() ui: PlayerUIState = new PlayerUIState()
  @Provider() vm: PlayerViewModel = new PlayerViewModel(this.ui)
  castController: avSession.AVCastController | undefined = undefined;
  backClickListener: () => void = () => {
    HMRouterMgr.pop()
  }
  @Local appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  @Local lifecycle: PlayerLifecycle = HMRouterMgr.getCurrentLifecycleOwner()?.getLifecycle() as PlayerLifecycle;

  @Monitor('lifecycle.options')
  onNewInstance() {
    this.options = this.lifecycle.options
    this.ui = new PlayerUIState()
    this.vm = new PlayerViewModel(this.ui)
    this.aboutToAppear()
  }

  @Monitor('vm.queueManager.currentMediaSourceOrNull')
  onMediaSourceChanged() {
    if (!this.vm.queueManager.currentMediaSourceOrNull) {
      return
    }
    let mediaSource = this.vm.queueManager.currentMediaSourceOrNull!
    if (MediaStreamUtil.isLandscape(mediaSource.selectedVideoStream) == false) {
      // For portrait videos, immediately enable fullscreen
      //playerFullscreenHelper.enableFullscreen()

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

  async aboutToAppear(): Promise<void> {
    if (OneMoreUtil.isBigScreen()) {
      if (this.appUIState.orientation == display.Orientation.LANDSCAPE ||
        this.appUIState.orientation == display.Orientation.LANDSCAPE_INVERTED) {
        //如果现在横屏分栏展示,需要手动点击才能全屏
        OneMoreUtil.updateNavModifier(this.appUIState)
      }
    }
    this.vm.initAVSession()
    // let callback = async () => {
    //   let defaultDisplay = display.getDefaultDisplaySync()
    //   this.appUIState.isFullScreen = defaultDisplay.width > defaultDisplay.height
    // }
    // try {
    //   display.on("change", callback); // 监听屏幕状态改变
    // } catch (exception) {
    //   console.error('PlayerScreen', 'Failed to register callback. Code: ' + JSON.stringify(exception));
    // }
    emitter.on(Constants.PLAYER_EVENT, (data) => {
      if (data.data && data.data['eventName']) {
        let name = data.data['eventName'] as string
        switch (name) {
          case 'onBackPressed':
            this.onBackPress()
            break
        }
      }
    })
    switch (this.vm.queueManager.initializePlaybackQueue(this.options)) {
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
    // Create playback menus
    this.vm.ui.playerMenus = new PlayerMenus() //(this, playerBinding, playerControlsBinding)
  }

  aboutToDisappear() {
    this.vm.reportPlaybackStop()
    emitter.off(Constants.PLAYER_EVENT.eventId)
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
    if (this.appUIState.isFullScreen) {
      this.appUIState.isFullScreen = false
    } else {
      HMRouterMgr.pop()
    }
  }

  build() {
    //播放器
    Stack() {
      if (this.ui.url) {
        PlayerView({
          videoUrl: this.ui.url,
          onNext: () => {
            this.vm.queueManager.next()
          },
          onAudioTrackSelected: (stream: MediaStream) => {
            this.vm.trackSelectionHelper.selectAudioTrack(stream)
          },
          onSubtitleSelected: (stream: MediaStream) => {
            this.vm.trackSelectionHelper.selectSubtitleTrack(stream)
          },
          onQualitySelected: (option: QualityOption) => {
            this.vm.changeBitrate(option.bitrate)
          },
          stopCast: () => {
            this.vm.stopCast()
          },
          onEpisodeChange: (index: number) => {
            this.vm.changeEpisode(index)
          }
        }).width('100%')
          .height('100%')
      }
    }
    .alignContent(Alignment.Top)
    .width('100%')
    .height('100%')
    .backgroundColor('#111111')
  }
}