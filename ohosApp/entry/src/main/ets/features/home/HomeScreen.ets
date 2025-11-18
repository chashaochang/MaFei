import { AppUIState } from "../../entity/AppUIState";
import { HomeTab } from "./hometab/HomeTab";
import { MediaTab } from "./mediatab/MediaTab";
import { MineTab } from "./minetab/MineTab";
import { AppStorageV2 } from "@kit.ArkUI";
import { UIConstants } from "../../common/UIConstants";

@ComponentV2
export struct HomeScreen {
  @Local currentIndex: number = 0;
  @Local selectedIndex: number = 0
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  build() {
    Column() {
      Tabs({ barPosition: BarPosition.End }) {
        TabContent() {
          HomeTab()
        }
        .tabBar(this.tabBuilder('首页', 0, $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')))

        TabContent() {
          MediaTab()
        }
        .tabBar(this.tabBuilder('媒体库', 1, $r('app.media.ic_media_sel'), $r('app.media.ic_media_store')))

        TabContent() {
          MineTab()
        }
        .tabBar(this.tabBuilder('我', 2, $r('app.media.ic_mine_sel'), $r('app.media.ic_mine')))
      }
      .barHeight(UIConstants.BOTTOM_BAR_HEIGHT + this.appUIState.safeBottom)
      .barBackgroundColor($r('app.color.tab'))
      .barOverlap(true)
      .onChange((index: number) => {
        this.currentIndex = index;
        this.selectedIndex = index
      })
      .onAnimationStart((index: number, targetIndex: number, event: TabsAnimationEvent) => {
        if (index === targetIndex) {
          return
        }
        // selectedIndex控制自定义TabBar内Image和Text颜色切换
        this.selectedIndex = targetIndex
      })
      Blank().height(this.appUIState.safeBottom)
    }
  }

  @Builder
  tabBuilder(title: string, targetIndex: number, selectedImg: Resource, normalImg: Resource) {
    Column({ space: 4 }) {
      Image(this.selectedIndex === targetIndex ? selectedImg : normalImg)
        .size({ width: 20, height: 20 })
      Text(title)
        .fontSize(14)
        .fontColor($r('app.color.text_primary'))
    }
    .width('100%')
    .height(UIConstants.BOTTOM_BAR_HEIGHT + this.appUIState.safeBottom)
    .padding({
      bottom: this.appUIState.safeBottom,
    })
    .justifyContent(FlexAlign.Center)
  }
}