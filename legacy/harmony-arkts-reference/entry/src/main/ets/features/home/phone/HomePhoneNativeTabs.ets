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
import { HomeTab } from '../hometab/HomeTab'
import { FavoriteListPage } from '../../favorite/FavoriteListPage'
import { MediaTab } from '../mediatab/MediaTab'
import { MineTab } from '../minetab/MineTab'

@ComponentV2
struct HomeNativeTabBarItem {
  @Require @Param title: string
  @Require @Param targetIndex: number
  @Require @Param icon: Resource
  @Require @Param selectedIndex: number
  @Param systemSymbol: boolean = false
  @Param badgeCount: number = 0

  build() {
    Column({ space: 4 }) {
      Stack({ alignContent: Alignment.TopEnd }) {
        if (this.systemSymbol) {
          SymbolGlyph(this.icon)
            .fontSize(22)
            .fontColor([this.selectedIndex === this.targetIndex ?
              $r('app.color.native_navigation_accent') : $r('sys.color.icon_primary')])
        } else {
          Image(this.icon)
            .size({ width: 22, height: 22 })
            .fillColor(this.selectedIndex === this.targetIndex ?
              $r('app.color.native_navigation_accent') : $r('sys.color.icon_primary'))
        }
        if (this.badgeCount > 0) {
          Text(this.badgeCount > 99 ? '99+' : this.badgeCount.toString())
            .fontSize(9)
            .fontWeight(FontWeight.Bold)
            .fontColor(Color.White)
            .height(14)
            .width(this.badgeCount > 9 ? 22 : 14)
            .padding({ left: 4, right: 4 })
            .backgroundColor('#ff4d4f')
            .borderRadius(7)
            .translate({ x: 8, y: -5 })
        }
      }
      .width(34)
      .height(24)
      .alignContent(Alignment.Center)
      Text(this.title)
        .fontSize(11)
        .fontColor(this.selectedIndex === this.targetIndex ?
          $r('app.color.native_navigation_accent') : $r('sys.color.font_primary'))
    }
    .height('100%')
    .width('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
export struct HomePhoneNativeTabs {
  @Require @Param selectedIndex: number
  @Require @Param safeBottom: number
  @Require @Param compactPlayerChrome: boolean
  @Require @Param homeContentScroller: Scroller
  @Require @Param favoriteContentScroller: Scroller
  @Require @Param mediaContentScroller: Scroller
  @Require @Param mineContentScroller: Scroller
  @Require @Param contentTopInset: number
  @Require @Param contentBottomInset: number
  @Require @Param onSelectedIndexChange: (index: number) => void
  @Require @Param onMediaAdministratorStateChange: (isAdministrator: boolean) => void
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

  // CustomBuilderAdapter: home-phone-tab-bar-item
  @Builder
  private tabBarItem(title: string, targetIndex: number, icon: Resource, systemSymbol: boolean = false) {
    HomeNativeTabBarItem({
      title,
      targetIndex,
      icon,
      selectedIndex: this.selectedIndex,
      systemSymbol
    })
  }



  build() {
    Stack({ alignContent: Alignment.BottomStart }) {
      HdsTabs({
        index: this.visibleTabIndex(this.selectedIndex),
        barPosition: BarPosition.End,
        controller: this.tabsController
      }) {
      TabContent() {
        HomeTab({
          active: this.selectedIndex === 0,
          contentBottomInset: this.contentBottomInset,
          contentScroller: this.homeContentScroller
        })
      }
      .tabBar(this.tabBarItem('首页', 0, $r('app.media.ic_home_sel')))
      TabContent() {
        FavoriteListPage({
          fromHome: true,
          contentTopInset: this.contentTopInset,
          contentBottomInset: this.contentBottomInset,
          contentScroller: this.favoriteContentScroller
        })
      }
      .tabBar(this.tabBarItem('收藏', 2, $r('app.media.ic_favorite_sel')))
      TabContent() {
        MediaTab({
          contentBottomInset: this.contentBottomInset,
          rootTitleBarOwned: true,
          contentScroller: this.mediaContentScroller,
          onAdministratorStateChange: (isAdministrator: boolean) => {
            this.onMediaAdministratorStateChange(isAdministrator)
          }
        })
      }
      .tabBar(this.tabBarItem('媒体库', 3, $r('app.media.ic_media_sel')))
      TabContent() {
        MineTab({
          contentBottomInset: this.contentBottomInset,
          compactTopInset: true,
          contentScroller: this.mineContentScroller
        })
      }
      .tabBar(this.tabBarItem('我的', 4, $r('sys.symbol.person_fill'), true))
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
