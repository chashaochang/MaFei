import { UIConstants } from '../../../common/UIConstants'
import { CustomTab } from '../../../component/tabbar/CustomTab'
import { HomeDestination } from '../HomeDestination'
import { HomePhoneChromeTab } from './HomePhoneChromeTab'

@ComponentV2
export struct HomePhoneStandardTabs {
  @Require @Param selectedIndex: number
  @Require @Param chasingBadgeCount: number
  @Require @Param navigationChromeInset: number
  @Require @Param onSelectedIndexChange: (index: number) => void
  private tabsController: TabsController = new TabsController()
  private pendingSelectedIndex: number = -1

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

  private safeBottom(): number {
    return Math.max(0, this.navigationChromeInset - UIConstants.BOTTOM_BAR_HEIGHT)
  }

  build() {
    Stack({ alignContent: Alignment.Bottom }) {
      Tabs({
        index: this.selectedIndex,
        barPosition: BarPosition.End,
        controller: this.tabsController
      }) {
        TabContent() {
          HomePhoneChromeTab({ destination: HomeDestination.Home })
        }
        TabContent() {
          HomePhoneChromeTab({ destination: HomeDestination.Chasing })
        }
        TabContent() {
          HomePhoneChromeTab({ destination: HomeDestination.Favorite })
        }
        TabContent() {
          HomePhoneChromeTab({ destination: HomeDestination.Media })
        }
        TabContent() {
          HomePhoneChromeTab({ destination: HomeDestination.Mine })
        }
      }
      .barHeight(0)
      .barWidth(0)
      .height(0)
      .vertical(false)
      .scrollable(false)
      .animationDuration(0)
      .onChange((index: number) => {
        this.notifySelectedIndexChange(index)
      })

      CustomTab({
        selectIndex: this.selectedIndex,
        chasingBadgeCount: this.chasingBadgeCount,
        onItemClick: (index: number) => {
          this.notifySelectedIndexChange(index)
          this.tabsController.changeIndex(index)
        }
      })
        .margin({ bottom: this.safeBottom() })
    }
    .width('100%')
    .height(this.navigationChromeInset)
  }
}
