import { LengthMetrics } from '@kit.ArkUI'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { HdsUiCapability } from '../../../theme/HdsUiCapability'

const HOME_LIBRARY_CHIP_GAP: number = 8

export interface HomeFeedTabItem {
  id: string
  label: string
}

@ComponentV2
export struct HomeLibraryChipSelector {
  @Require @Param tabs: HomeFeedTabItem[]
  @Require @Param selectedId: string
  @Require @Param onSelectedIdChange: (id: string) => void
  @Param nativeSurface: boolean = false
  @Param fadeRightEdge: boolean = false
  @Param rightContentPadding: number = 0
  private chipScroller: Scroller = new Scroller()

  private selectedIndex(): number {
    const index = this.tabs.findIndex((item: HomeFeedTabItem): boolean => {
      return item.id === this.selectedId
    })
    return index >= 0 ? index : 0
  }

  private revealSelected(smooth: boolean): void {
    const index = this.selectedIndex()
    setTimeout(() => {
      this.chipScroller.scrollToIndex(index, smooth, ScrollAlign.CENTER)
    }, 0)
  }

  private selectIndex(index: number): void {
    if (index < 0 || index >= this.tabs.length) {
      return
    }
    const id = this.tabs[index].id
    this.chipScroller.scrollToIndex(index, true, ScrollAlign.CENTER)
    if (id !== this.selectedId) {
      this.onSelectedIdChange(id)
    }
  }

  @Monitor('selectedId')
  onSelectedIdMonitor(): void {
    this.revealSelected(true)
  }

  @Monitor('tabs')
  onTabsChange(): void {
    this.revealSelected(false)
  }

  aboutToAppear(): void {
    this.revealSelected(false)
  }

  private useImmersiveChipSurface(): boolean {
    return this.nativeSurface && HdsUiCapability.supportsSystemMaterial()
  }

  build() {
    List({ space: HOME_LIBRARY_CHIP_GAP, scroller: this.chipScroller }) {
      ForEach(this.tabs, (tab: HomeFeedTabItem, index: number) => {
        ListItem() {
          if (this.useImmersiveChipSurface()) {
            Text(tab.label)
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(index === this.selectedIndex() ? Color.Black : $r('sys.color.font_primary'))
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .height(36)
              .padding({ left: 14, right: 14 })
              .borderRadius(18)
              .backgroundColor(Color.Transparent)
              .systemMaterial(AppThemeSurfaceResolver.material(index === this.selectedIndex() ?
                AppThemeMaterialRole.HomeLibraryChipSelected : AppThemeMaterialRole.HomeLibraryChip))
              .onClick((): void => {
                this.selectIndex(index)
              })
          } else {
            Text(tab.label)
              .fontSize(14)
              .fontWeight(FontWeight.Medium)
              .fontColor(index === this.selectedIndex() ? Color.White : $r('app.color.text_1'))
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
              .height(36)
              .padding({ left: 14, right: 14 })
              .borderRadius(18)
              .backgroundColor(index === this.selectedIndex() ?
                $r('app.color.color_main') : $r('app.color.bg_1'))
              .onClick((): void => {
                this.selectIndex(index)
              })
          }
        }
      }, (tab: HomeFeedTabItem) => tab.id)
    }
      .width('100%')
      .height(40)
      .padding({ right: this.rightContentPadding })
      .listDirection(Axis.Horizontal)
      .scrollBar(BarState.Off)
      .fadingEdge(this.fadeRightEdge, {
        fadingEdgeLength: LengthMetrics.vp(32)
      })
      .clip(true)
  }
}
