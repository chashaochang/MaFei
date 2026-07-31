import {
  ChipV2,
  ChipV2Label,
  ChipV2Options,
  ChipV2Size,
  ColorMetrics,
  LengthMetrics
} from '@kit.ArkUI'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'

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

  private chipOptions(tab: HomeFeedTabItem, index: number): ChipV2Options {
    return new ChipV2Options({
      label: new ChipV2Label({
        text: tab.label,
        fontColor: ColorMetrics.resourceColor($r('sys.color.font_primary')),
        activatedFontColor: ColorMetrics.numeric(0x000000)
      }),
      size: ChipV2Size.NORMAL,
      allowClose: false,
      activated: index === this.selectedIndex(),
      backgroundColor: ColorMetrics.rgba(255, 255, 255, 0.08),
      activatedBackgroundColor: ColorMetrics.rgba(255, 255, 255, 0.82),
      backgroundSystemMaterial: AppThemeSurfaceResolver.material(
        AppThemeMaterialRole.HomeLibraryChip),
      activatedBackgroundSystemMaterial: AppThemeSurfaceResolver.material(
        AppThemeMaterialRole.HomeLibraryChipSelected),
      onClicked: (): void => {
        this.selectIndex(index)
      }
    })
  }

  build() {
    List({ space: HOME_LIBRARY_CHIP_GAP, scroller: this.chipScroller }) {
      ForEach(this.tabs, (tab: HomeFeedTabItem, index: number) => {
        ListItem() {
          ChipV2({
            chipV2Options: this.chipOptions(tab, index)
          })
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
