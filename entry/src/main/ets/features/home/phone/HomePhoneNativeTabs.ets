import {
  DividerMode,
  HdsAnimationMode,
  hdsMaterial,
  HdsTabs,
  HdsTabsController,
  HdsTabsModifier
} from '@kit.UIDesignKit'
import { UIConstants } from '../../../common/UIConstants'
import { MusicHostVisibilityPolicy } from '../../music/MusicHostVisibilityPolicy'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { NativeThemeConstructionProbe } from '../../../theme/NativeThemeConstructionProbe'

@ComponentV2
export struct HomePhoneNativeTabs {
  @Require @Param selectedIndex: number
  @Require @Param safeBottom: number
  @Require @Param compactPlayerChrome: boolean
  @Require @Param homeContentScroller: Scroller
  @Require @Param onSelectedIndexChange: (index: number) => void
  @BuilderParam homeContentBuilder: () => void
  @BuilderParam favoriteContentBuilder: () => void
  @BuilderParam mediaContentBuilder: () => void
  @BuilderParam mineContentBuilder: () => void
  private tabsController: HdsTabsController = this.createTabsController()
  private pendingSelectedIndex: number = -1

  private createTabsController(): HdsTabsController {
    NativeThemeConstructionProbe.markHdsNavigationConstruction()
    return new HdsTabsController()
  }

  @Monitor('selectedIndex')
  onExternalIndexChange(): void {
    this.pendingSelectedIndex = -1
    if (this.selectedIndex === 1) {
      this.notifySelectedIndexChange(0)
      this.tabsController.changeIndex(0)
      return
    }
    this.tabsController.changeIndex(this.visibleTabIndex(this.selectedIndex))
  }

  @Monitor('compactPlayerChrome')
  onCompactPlayerChromeChange(): void {
    if (this.compactPlayerChrome) {
      this.tabsController.applyHideAnimation(HdsAnimationMode.SCROLL_ANIMATION)
    } else {
      this.tabsController.applyShowAnimation(HdsAnimationMode.SCROLL_ANIMATION)
    }
  }

  private notifySelectedIndexChange(index: number): void {
    if (index === this.selectedIndex || index === this.pendingSelectedIndex) {
      if (index === 0 && this.compactPlayerChrome) {
        this.homeContentToTop()
      }
      return
    }
    this.pendingSelectedIndex = index
    this.onSelectedIndexChange(index)
  }

  private homeContentToTop(): void {
    this.homeContentScroller.scrollEdge(Edge.Start)
  }

  private normalizedSafeBottom(): number {
    return Math.max(0, this.safeBottom)
  }

  private visibleTabIndex(destinationIndex: number): number {
    if (destinationIndex === 2) {
      return 1
    }
    if (destinationIndex === 3) {
      return 2
    }
    if (destinationIndex === 4) {
      return 3
    }
    return 0
  }

  private destinationIndex(tabIndex: number): number {
    if (tabIndex === 1) {
      return 2
    }
    if (tabIndex === 2) {
      return 3
    }
    if (tabIndex === 3) {
      return 4
    }
    return 0
  }

  aboutToAppear(): void {
    if (this.selectedIndex === 1) {
      this.notifySelectedIndexChange(0)
    }
    this.onCompactPlayerChromeChange()
  }

  private tabsModifier(): HdsTabsModifier {
    const modifier = new HdsTabsModifier()
    modifier
      .vertical(false)
      .barPosition(BarPosition.End)
      .barHeight(UIConstants.BOTTOM_BAR_HEIGHT)
      .barFloatingStyle({
        adaptToHandedness: true,
        barBottomMargin: this.normalizedSafeBottom(),
        systemMaterialEffect: {
          materialType: hdsMaterial.MaterialType.IMMERSIVE,
          materialLevel: hdsMaterial.MaterialLevel.ADAPTIVE,
        },
      })
    return modifier
  }

  @Builder
  private tabBuilder(title: string, targetIndex: number, selectedImg: Resource,
    normalImg: Resource, badgeCount: number = 0) {
    Column({ space: 4 }) {
      Stack({ alignContent: Alignment.TopEnd }) {
        Image(this.selectedIndex === targetIndex ? selectedImg : normalImg)
          .size({ width: 20, height: 20 })
          .fillColor(this.selectedIndex === targetIndex ?
            $r('app.color.color_main') : $r('sys.color.icon_primary'))
        if (badgeCount > 0) {
          Text(badgeCount > 99 ? '99+' : badgeCount.toString())
            .fontSize(9)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .height(14)
            .width(badgeCount > 9 ? 22 : 14)
            .padding({ left: 4, right: 4 })
            .backgroundColor('#ff4d4f')
            .borderRadius(7)
            .translate({ x: 8, y: -5 })
        }
      }
      .width(32)
      .height(22)
      .alignContent(Alignment.Center)
      Text(title)
        .fontSize(14)
        .fontColor(this.selectedIndex === targetIndex ?
          $r('app.color.color_main') : $r('sys.color.font_primary'))
    }
    .height('100%')
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }

  build() {
    Stack({ alignContent: Alignment.BottomStart }) {
      HdsTabs({
        index: this.visibleTabIndex(this.selectedIndex),
        barPosition: BarPosition.End,
        controller: this.tabsController
      }) {
      TabContent() {
        this.homeContentBuilder()
      }
      .tabBar(this.tabBuilder(
        '首页', 0, $r('app.media.ic_home_sel'), $r('app.media.ic_home_unsel')
      ))
      TabContent() {
        this.favoriteContentBuilder()
      }
      .tabBar(this.tabBuilder(
        '收藏', 2, $r('app.media.ic_favorite_sel'), $r('app.media.ic_favorite_unsel')
      ))
      TabContent() {
        this.mediaContentBuilder()
      }
      .tabBar(this.tabBuilder(
        '媒体库', 3, $r('app.media.ic_media_sel'), $r('app.media.ic_media_unsel')
      ))
      TabContent() {
        this.mineContentBuilder()
      }
      .tabBar(this.tabBuilder(
        '我的', 4, $r('app.media.ic_mine_sel'), $r('app.media.ic_mine_unsel')
      ))
      }
      .barHeight(UIConstants.BOTTOM_BAR_HEIGHT)
      .barMode(BarMode.Fixed, { nonScrollableLayoutStyle: LayoutStyle.ALWAYS_CENTER })
      .vertical(false)
      .divider({ mode: DividerMode.NONE })
      .barOverlap(true)
      .animationDuration(0)
      .attributeModifier(this.tabsModifier())
      .scrollable(false)
      .width('100%')
      .height('100%')
      .onTabBarClick((index: number) => {
        this.notifySelectedIndexChange(this.destinationIndex(index))
      })

      if (this.compactPlayerChrome) {
        Button({ type: ButtonType.Circle, stateEffect: true }) {
          Image($r('app.media.ic_home_sel'))
            .width(22)
            .height(22)
            .fillColor($r('app.color.color_main'))
        }
        .width(MusicHostVisibilityPolicy.COMPACT_HOME_BUTTON_SIZE)
        .height(MusicHostVisibilityPolicy.COMPACT_HOME_BUTTON_SIZE)
        .borderRadius(MusicHostVisibilityPolicy.COMPACT_HOME_BUTTON_SIZE / 2)
        .buttonStyle(ButtonStyleMode.TEXTUAL)
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.MusicMiniPlayer,
          true
        ))
        .accessibilityText('首页')
        .margin({
          left: MusicHostVisibilityPolicy.COMPACT_HOME_SIDE_MARGIN,
          bottom: this.normalizedSafeBottom() + MusicHostVisibilityPolicy.MINI_PLAYER_GAP
        })
        .onClick(() => {
          if (this.selectedIndex === 0) {
            this.homeContentToTop()
            return
          }
          this.notifySelectedIndexChange(0)
        })
      }
    }
    .width('100%')
    .height('100%')
  }
}
