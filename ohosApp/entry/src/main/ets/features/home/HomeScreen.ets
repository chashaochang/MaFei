import { AppUIState } from "../../entity/AppUIState";
import { HomeTab } from "./hometab/HomeTab";
import { MediaTab } from "./mediatab/MediaTab";
import { MineTab } from "./minetab/MineTab";
import { AppStorageV2 } from "@kit.ArkUI";
import { UIConstants } from "../../common/UIConstants";
import { BreakpointSystem, BreakpointTypeEnum } from "../../utils/BreakpointSystem";

@ComponentV2
export struct HomeScreen {
  @Local selectedIndex: number = 0
  private breakpointSystem: BreakpointSystem = new BreakpointSystem();

  aboutToAppear() {
    this.breakpointSystem.register(this.getUIContext());
  }

  aboutToDisappear() {
    this.breakpointSystem.unregister();
  }

  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  build() {
    Column() {
      Tabs({
        barPosition: (this.appUIState.currentBreakpoint === BreakpointTypeEnum.XS ||
          this.appUIState.currentBreakpoint === BreakpointTypeEnum.SM ||
          this.appUIState.currentBreakpoint === BreakpointTypeEnum.MD) ? BarPosition.End : BarPosition.Start
      }) {
        TabContent() {
          HomeTab()
        }
        .tabBar(this.tabBuilder('首页', 0, $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')))
        .padding({
          left: this.appUIState.currentBreakpoint.includes('l') ? 97 : 0
        })

        TabContent() {
          MediaTab()
        }
        .tabBar(this.tabBuilder('媒体库', 1, $r('app.media.ic_media_sel'), $r('app.media.ic_media_store')))
        .padding({
          left: this.appUIState.currentBreakpoint.includes('l') ? 97 : 0
        })

        TabContent() {
          MineTab()
        }
        .tabBar(this.tabBuilder('我', 2, $r('app.media.ic_mine_sel'), $r('app.media.ic_mine')))
        .padding({
          left: this.appUIState.currentBreakpoint.includes('l') ? 97 : 0
        })
      }
      .barHeight(this.appUIState.currentBreakpoint.includes('l') ? '100%' : UIConstants.BOTTOM_BAR_HEIGHT + this.appUIState.safeBottom)
      .barWidth(this.appUIState.currentBreakpoint.includes('l') ? 96 : '100%')
      .barMode(this.appUIState.currentBreakpoint.includes('l') ? BarMode.Scrollable : BarMode.Fixed,
        { nonScrollableLayoutStyle: LayoutStyle.ALWAYS_CENTER })
      .vertical(this.appUIState.currentBreakpoint.includes('l'))
      .barBackgroundColor($r('app.color.tab'))
      .barOverlap(true)
      .onChange((index: number) => {
        this.selectedIndex = index
      })
      .onAnimationStart((index: number, targetIndex: number, event: TabsAnimationEvent) => {
        if (index === targetIndex) {
          return
        }
        // selectedIndex控制自定义TabBar内Image和Text颜色切换
        this.selectedIndex = targetIndex
      })
      .scrollable(false)
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