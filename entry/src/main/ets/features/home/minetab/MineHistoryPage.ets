import { HMRouter } from '@hadss/hmrouter'
import { RouterConsts } from '../../../common/RouterConsts'
import { playMediaRefs } from '../../../common/CommonFunc'
import { AppRouteDestination } from '../../../component/AppRouteDestination'
import { BreakpointTypeEnum } from '../../../utils/BreakpointSystem'
import { MineHistoryItem } from './MineHistoryPlanner'
import { MineHistoryPageState, MineHistoryPageUIState } from './MineHistoryPageUIState'
import { MineHistoryPageViewModel } from './MineHistoryPageViewModel'
import { MineHistoryPoster } from './MineHistoryPoster'

@HMRouter({
  pageUrl: RouterConsts.MineHistoryPage,
  useNavDst: true
})
@ComponentV2
export struct MineHistoryPage {
  private readonly contentScroller: Scroller = new Scroller()
  @Local ui: MineHistoryPageUIState = new MineHistoryPageUIState(this.getUIContext())
  @Local vm: MineHistoryPageViewModel = new MineHistoryPageViewModel(this.ui)

  aboutToAppear(): void {
    this.vm.loadInitial()
  }

  private columnCount(): number {
    switch (this.vm.appUIState.currentBreakpoint) {
      case BreakpointTypeEnum.LG:
        return 4
      case BreakpointTypeEnum.XL:
        return 6
      case BreakpointTypeEnum.XXL:
        return 8
      default:
        return 3
    }
  }

  private columnsTemplate(): string {
    return '1fr '.repeat(this.columnCount())
  }

  private horizontalPadding(): number {
    return this.vm.appUIState.currentBreakpoint === BreakpointTypeEnum.XS ||
      this.vm.appUIState.currentBreakpoint === BreakpointTypeEnum.SM ? 16 : 24
  }

  private skeletonItems(): number[] {
    const items: number[] = []
    for (let index = 0; index < this.columnCount() * 3; index++) {
      items.push(index)
    }
    return items
  }

  private historyItemKey(item: MineHistoryItem): string {
    return `${item.mediaRef.identity}|${item.latestSeasonNumber || 0}|${item.latestEpisodeNumber || 0}|` +
      `${item.seriesHasMultipleSeasons ? 1 : 0}`
  }

  @Builder
  private loadingGrid(nativeSurface: boolean) {
    Grid() {
      ForEach(this.skeletonItems(), (_index: number) => {
        GridItem() {
          Column({ space: 8 }) {
            Column()
              .width('100%')
              .aspectRatio(2 / 3)
              .borderRadius(10)
              .backgroundColor(nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
            Column()
              .width('72%')
              .height(13)
              .borderRadius(6.5)
              .backgroundColor(nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
          }
          .alignItems(HorizontalAlign.Start)
        }
      })
    }
    .columnsTemplate(this.columnsTemplate())
    .columnsGap(10)
    .rowsGap(14)
    .scrollBar(BarState.Off)
    .opacity(0.7)
  }

  @Builder
  private emptyState(nativeSurface: boolean) {
    Column({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.xmark_video'))
        .fontSize(42)
        .fontColor([nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2')])
      Text($r('app.string.mine_history_empty'))
        .fontSize(14)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private errorState(nativeSurface: boolean) {
    Column({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(42)
        .fontColor([nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2')])
      Text($r('app.string.mine_history_retry'))
        .fontSize(14)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
      Button($r('app.string.management_retry'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => this.vm.loadInitial())
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private loadMoreFooter(nativeSurface: boolean) {
    if (this.ui.loadingMore) {
      Row({ space: 8 }) {
        LoadingProgress()
          .width(20)
          .height(20)
        Text($r('app.string.mine_history_loading_more'))
          .fontSize(13)
          .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
      }
      .height(52)
      .justifyContent(FlexAlign.Center)
    } else if (this.ui.loadMoreFailed) {
      Button($r('app.string.mine_history_load_more_failed'), { type: ButtonType.Normal })
        .height(44)
        .fontSize(13)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .backgroundColor(Color.Transparent)
        .borderRadius(22)
        .onClick(() => this.vm.retryLoadMore())
    }
  }

  @Builder
  private historyGrid(nativeSurface: boolean) {
    Grid(this.contentScroller) {
      ForEach(this.ui.items, (item: MineHistoryItem) => {
        GridItem() {
          MineHistoryPoster({
            item,
            nativeSurface
          })
        }
        .onClick(() => playMediaRefs([item.mediaRef], this.getUIContext().getHostContext()!))
      }, (item: MineHistoryItem) => this.historyItemKey(item))
      if (this.ui.loadingMore || this.ui.loadMoreFailed) {
        GridItem() {
          this.loadMoreFooter(nativeSurface)
        }
        .columnStart(0)
        .columnEnd(this.columnCount() - 1)
      }
      GridItem() {
        Column()
          .height(Math.max(16, this.vm.appUIState.safeBottom + 16))
      }
      .columnStart(0)
      .columnEnd(this.columnCount() - 1)
    }
    .width('100%')
    .height('100%')
    .columnsTemplate(this.columnsTemplate())
    .columnsGap(10)
    .rowsGap(14)
    .scrollBar(BarState.Off)
    .onReachEnd(() => this.vm.loadMore())
  }

  @Builder
  private pageContent(nativeSurface: boolean) {
    Stack() {
      Column() {
        if (this.ui.historyState === MineHistoryPageState.Loading) {
          this.loadingGrid(nativeSurface)
        } else if (this.ui.historyState === MineHistoryPageState.Empty) {
          this.emptyState(nativeSurface)
        } else if (this.ui.historyState === MineHistoryPageState.Error) {
          this.errorState(nativeSurface)
        } else {
          this.historyGrid(nativeSurface)
        }
      }
      .width(`calc(100% - ${this.horizontalPadding() * 2}vp)`)
      .height('100%')
      .constraintSize({ maxWidth: 960 })
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
    .backgroundColor(nativeSurface ? Color.Transparent : $r('app.color.bg_main'))
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.mine_history_title'),
      scrollControllers: [this.contentScroller],
      contentBuilder: () => {
        this.pageContent(true)
      },
      legacyContentBuilder: () => {
        this.pageContent(false)
      }
    })
  }
}
