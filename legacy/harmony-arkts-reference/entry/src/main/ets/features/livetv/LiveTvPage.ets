import { HMRouter, HMRouterMgr } from "@hadss/hmrouter"
import { RouterConsts } from "../../common/RouterConsts"
import { LiveTvUIState } from "./LiveTvUIState";
import { LiveTvViewModel } from "./LiveTvViewModel";
import { LivePlayerView } from "../player/LivePlayerView";
import { PlayerFullscreenHelper } from "../../player/ui/PlayerFullscreenHelper";
import { emitter } from "@kit.BasicServicesKit";
import { Constants } from "../../utils/Constants";
import { WindowUtil } from '../../utils/WindowUtil'
import { OneMoreUtil } from '../../utils/OneMoreUtil'

@HMRouter({
  pageUrl: RouterConsts.LiveTvPage,
  singleton: true,
  lifecycle: 'playerLifecycle',
})
@ComponentV2
export struct LiveTvPage {
  @Provider() ui: LiveTvUIState = new LiveTvUIState(this.getUIContext());
  @Provider() vm: LiveTvViewModel = new LiveTvViewModel(this.ui);
  playerFullscreenHelper: PlayerFullscreenHelper = new PlayerFullscreenHelper()
  @Local mContext: object | undefined = undefined;

  @Monitor('vm.queueManager.currentMediaSourceOrNull')
  onMediaSourceChanged() {
    if (!this.vm.queueManager.currentMediaSourceOrNull) {
      return
    }
    //iptv自动横屏
    this.playerFullscreenHelper.enableFullscreen()
  }

  aboutToAppear() {
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
    this.vm.getChannelList()
  }

  aboutToDisappear() {
    // this.vm.reportPlaybackStop()
    emitter.off(Constants.PLAYER_EVENT.eventId)
    // this.vm.release()
  }

  onBackPress(): boolean | void {
    this.vm.appUIState.isFullScreen = false
    HMRouterMgr.pop({ navigationId: Constants.NAVIGATION_ID })
  }

  private toggleFullscreen(): void {
    if (this.vm.appUIState.isFullScreen) {
      this.vm.appUIState.isFullScreen = false
      this.vm.appUIState.windowClass?.setWindowSystemBarEnable(['status', 'navigation'])
      if (OneMoreUtil.shouldRequestPlayerManualRotation(this.vm.appUIState.windowClass)) {
        WindowUtil.setMainWindowPortrait(getContext(this))
      }
      return
    }
    this.vm.appUIState.isFullScreen = true
    this.vm.appUIState.windowClass?.setWindowSystemBarEnable([])
    this.playerFullscreenHelper.enableFullscreen()
  }

  build() {
    Stack() {
      if (this.ui.url) {
        LivePlayerView({
          videoUrl: this.ui.url!,
          onBack: (): void => {
            this.onBackPress()
          },
          onNext:()=>{
            this.vm.playNext()
          },
          onEpisodeChange:(index)=>{

          },
          onFullscreenToggle: (): void => {
            this.toggleFullscreen()
          }
        })
      }
    }
    .backgroundColor(Color.Black)
    .height('100%')
    .width('100%')
  }

}
