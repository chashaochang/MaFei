import {
  DividerMode,
  hdsMaterial,
  HdsTabs,
  HdsTabsController,
  HdsTabsModifier
} from '@kit.UIDesignKit'
import { UIConstants } from '../../../common/UIConstants'
import { NativeThemeConstructionProbe } from '../../../theme/NativeThemeConstructionProbe'

@ComponentV2
export struct HomePhoneNativeTabs {
  @Require @Param selectedIndex: number
  @Require @Param chasingBadgeCount: number
  @Require @Param safeBottom: number
  @Require @Param onSelectedIndexChange: (index: number) => void
  @BuilderParam homeContentBuilder: () => void
  @BuilderParam chasingContentBuilder: () => void
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
    this.tabsController.changeIndex(this.selectedIndex)
  }

  private notifySelectedIndexChange(index: number): void {
    if (index === this.selectedIndex || index === this.pendingSelectedIndex) {
      return
    }
    this.pendingSelectedIndex = index
    this.onSelectedIndexChange(index)
  }

  private normalizedSafeBottom(): number {
    return Math.max(0, this.safeBottom)
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
    HdsTabs({
      index: this.selectedIndex,
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
        this.chasingContentBuilder()
      }
      .tabBar(this.tabBuilder(
        '追剧', 1, $r('app.media.history'), $r('app.media.history'),
        this.chasingBadgeCount
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
    .onChange((index: number) => {
      this.notifySelectedIndexChange(index)
    })
  }
}
