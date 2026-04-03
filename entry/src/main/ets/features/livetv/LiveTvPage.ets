import { HMRouter, HMRouterMgr } from "@hadss/hmrouter"
import { RouterConsts } from "../../common/RouterConsts"
import { LiveTvUIState } from "./LiveTvUIState";
import { LiveTvViewModel } from "./LiveTvViewModel";
import { LivePlayerView } from "../player/LivePlayerView";
import { PlayerFullscreenHelper } from "../../player/ui/PlayerFullscreenHelper";
import { emitter } from "@kit.BasicServicesKit";
import { Constants } from "../../utils/Constants";

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
    HMRouterMgr.pop()
  }

  build() {
    Stack() {
      if (this.ui.url) {
        LivePlayerView({
          videoUrl: this.ui.url!,
          onNext:()=>{
            this.vm.playNext()
          },
          onEpisodeChange:(index)=>{

          }
        })
      }
    }
    .backgroundColor(Color.Black)
    .height('100%')
    .width('100%')
  }

}