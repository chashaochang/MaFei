import { HMRouter } from '@hadss/hmrouter'
import { RouterConsts } from '../../../common/RouterConsts'
import { playMediaRefs } from '../../../common/CommonFunc'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination'
import { BreakpointTypeEnum } from '../../../utils/BreakpointSystem'
import { MineHistoryItem } from './MineHistoryPlanner'
import { MineHistoryPageState, MineHistoryPageUIState } from './MineHistoryPageUIState'
import { MineHistoryPageViewModel } from './MineHistoryPageViewModel'
import { MineHistoryPoster } from './MineHistoryPoster'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct MineHistoryPageLoadingGrid {
  @Require @Param gridTemplate: string
  @Require @Param columnCount: number
  @Require @Param nativeSurface: boolean

  private skeletonItems(): number[] {
    const items: number[] = []
    for (let index = 0; index < this.columnCount * 3; index++) {
      items.push(index)
    }
    return items
  }

  build() {
    Grid() {
      ForEach(this.skeletonItems(), (_index: number) => {
        GridItem() {
          Column({ space: 8 }) {
            Column()
              .width('100%')
              .aspectRatio(2 / 3)
              .borderRadius(10)
              .backgroundColor(this.nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
            Column()
              .width('72%')
              .height(13)
              .borderRadius(6.5)
              .backgroundColor(this.nativeSurface ?
                $r('sys.color.comp_background_tertiary') : $r('app.color.bg_2'))
          }
          .alignItems(HorizontalAlign.Start)
        }
      })
    }
    .columnsTemplate(this.gridTemplate)
    .columnsGap(10)
    .rowsGap(14)
    .scrollBar(BarState.Off)
    .opacity(0.7)
  }
}

@ComponentV2
struct MineHistoryPageEmptyState {
  @Require @Param nativeSurface: boolean

  build() {
    Column({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.xmark_video'))
        .fontSize(42)
        .fontColor([this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2')])
      Text($r('app.string.mine_history_empty'))
        .fontSize(14)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct MineHistoryPageErrorState {
  @Require @Param nativeSurface: boolean
  @Event onRetry: () => void = () => {}

  build() {
    Column({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(42)
        .fontColor([this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2')])
      Text($r('app.string.mine_history_retry'))
        .fontSize(14)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
      Button($r('app.string.management_retry'), { type: ButtonType.Normal })
        .height(44)
        .borderRadius(22)
        .onClick(() => this.onRetry())
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct MineHistoryPageLoadMoreFooter {
  @Param loadingMore: boolean = false
  @Param loadMoreFailed: boolean = false
  @Require @Param nativeSurface: boolean
  @Event onRetry: () => void = () => {}

  build() {
    if (this.loadingMore) {
      Row({ space: 8 }) {
        LoadingProgress()
          .width(20)
          .height(20)
        Text($r('app.string.mine_history_loading_more'))
          .fontSize(13)
          .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
      }
      .height(52)
      .justifyContent(FlexAlign.Center)
    } else if (this.loadMoreFailed) {
      Button($r('app.string.mine_history_load_more_failed'), { type: ButtonType.Normal })
        .height(44)
        .fontSize(13)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .backgroundColor(Color.Transparent)
        .borderRadius(22)
        .onClick(() => this.onRetry())
    }
  }
}

@ComponentV2
struct MineHistoryPageGrid {
  @Require @Param items: MineHistoryItem[]
  @Require @Param contentScroller: Scroller
  @Require @Param gridTemplate: string
  @Require @Param columnCount: number
  @Require @Param safeBottom: number
  @Require @Param nativeSurface: boolean
  @Param loadingMore: boolean = false
  @Param loadMoreFailed: boolean = false
  @Event onItemPressed: (item: MineHistoryItem) => void = (_item: MineHistoryItem) => {}
  @Event onLoadMore: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}

  private historyItemKey(item: MineHistoryItem): string {
    return `${item.mediaRef.identity}|${item.latestSeasonNumber || 0}|${item.latestEpisodeNumber || 0}|` +
      `${item.seriesHasMultipleSeasons ? 1 : 0}`
  }

  build() {
    Grid(this.contentScroller) {
      ForEach(this.items, (item: MineHistoryItem) => {
        GridItem() {
          MineHistoryPoster({
            item,
            nativeSurface: this.nativeSurface
          })
        }
        .onClick(() => this.onItemPressed(item))
      }, (item: MineHistoryItem) => this.historyItemKey(item))
      if (this.loadingMore || this.loadMoreFailed) {
        GridItem() {
          MineHistoryPageLoadMoreFooter({
            loadingMore: this.loadingMore,
            loadMoreFailed: this.loadMoreFailed,
            nativeSurface: this.nativeSurface,
            onRetry: () => this.onRetryLoadMore()
          })
        }
        .columnStart(0)
        .columnEnd(this.columnCount - 1)
      }
      GridItem() {
        Column()
          .height(Math.max(16, this.safeBottom + 16))
      }
      .columnStart(0)
      .columnEnd(this.columnCount - 1)
    }
    .width('100%')
    .height('100%')
    .columnsTemplate(this.gridTemplate)
    .columnsGap(10)
    .rowsGap(14)
    .scrollBar(BarState.Off)
    .onReachEnd(() => this.onLoadMore())
  }
}

@ComponentV2
export struct MineHistoryPageContent {
  @Require @Param historyState: MineHistoryPageState
  @Require @Param items: MineHistoryItem[]
  @Require @Param contentScroller: Scroller
  @Require @Param gridTemplate: string
  @Require @Param columnCount: number
  @Require @Param horizontalPadding: number
  @Require @Param safeBottom: number
  @Require @Param nativeSurface: boolean
  @Param loadingMore: boolean = false
  @Param loadMoreFailed: boolean = false
  @Event onRetry: () => void = () => {}
  @Event onItemPressed: (item: MineHistoryItem) => void = (_item: MineHistoryItem) => {}
  @Event onLoadMore: () => void = () => {}
  @Event onRetryLoadMore: () => void = () => {}

  build() {
    Stack() {
      Column() {
        if (this.historyState === MineHistoryPageState.Loading) {
          MineHistoryPageLoadingGrid({
            gridTemplate: this.gridTemplate,
            columnCount: this.columnCount,
            nativeSurface: this.nativeSurface
          })
        } else if (this.historyState === MineHistoryPageState.Empty) {
          MineHistoryPageEmptyState({ nativeSurface: this.nativeSurface })
        } else if (this.historyState === MineHistoryPageState.Error) {
          MineHistoryPageErrorState({
            nativeSurface: this.nativeSurface,
            onRetry: () => this.onRetry()
          })
        } else {
          MineHistoryPageGrid({
            items: this.items,
            contentScroller: this.contentScroller,
            gridTemplate: this.gridTemplate,
            columnCount: this.columnCount,
            safeBottom: this.safeBottom,
            nativeSurface: this.nativeSurface,
            loadingMore: this.loadingMore,
            loadMoreFailed: this.loadMoreFailed,
            onItemPressed: (item: MineHistoryItem) => this.onItemPressed(item),
            onLoadMore: () => this.onLoadMore(),
            onRetryLoadMore: () => this.onRetryLoadMore()
          })
        }
      }
      .width(`calc(100% - ${this.horizontalPadding * 2}vp)`)
      .height('100%')
      .constraintSize({ maxWidth: 960 })
    }
    .width('100%')
    .height('100%')
    .alignContent(Alignment.Top)
    .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_main'))
  }
}

@HMRouter({
  pageUrl: RouterConsts.MineHistoryPage,
  useNavDst: true
})
@ComponentV2
export struct MineHistoryPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
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

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.mine_history_title'),
        scrollControllers: [this.contentScroller]
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              MineHistoryPageContent({
                historyState: this.ui.historyState,
                items: this.ui.items,
                contentScroller: this.contentScroller,
                gridTemplate: this.columnsTemplate(),
                columnCount: this.columnCount(),
                horizontalPadding: this.horizontalPadding(),
                safeBottom: this.vm.appUIState.safeBottom,
                nativeSurface: true,
                loadingMore: this.ui.loadingMore,
                loadMoreFailed: this.ui.loadMoreFailed,
                onRetry: () => this.vm.loadInitial(),
                onItemPressed: (item: MineHistoryItem) =>
                  playMediaRefs([item.mediaRef], this.getUIContext().getHostContext()!),
                onLoadMore: () => this.vm.loadMore(),
                onRetryLoadMore: () => this.vm.retryLoadMore()
              })
            } else {
              MineHistoryPageContent({
                historyState: this.ui.historyState,
                items: this.ui.items,
                contentScroller: this.contentScroller,
                gridTemplate: this.columnsTemplate(),
                columnCount: this.columnCount(),
                horizontalPadding: this.horizontalPadding(),
                safeBottom: this.vm.appUIState.safeBottom,
                nativeSurface: false,
                loadingMore: this.ui.loadingMore,
                loadMoreFailed: this.ui.loadMoreFailed,
                onRetry: () => this.vm.loadInitial(),
                onItemPressed: (item: MineHistoryItem) =>
                  playMediaRefs([item.mediaRef], this.getUIContext().getHostContext()!),
                onLoadMore: () => this.vm.loadMore(),
                onRetryLoadMore: () => this.vm.retryLoadMore()
              })
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        MineHistoryPageContent({
          historyState: this.ui.historyState,
          items: this.ui.items,
          contentScroller: this.contentScroller,
          gridTemplate: this.columnsTemplate(),
          columnCount: this.columnCount(),
          horizontalPadding: this.horizontalPadding(),
          safeBottom: this.vm.appUIState.safeBottom,
          nativeSurface: false,
          loadingMore: this.ui.loadingMore,
          loadMoreFailed: this.ui.loadMoreFailed,
          onRetry: () => this.vm.loadInitial(),
          onItemPressed: (item: MineHistoryItem) =>
            playMediaRefs([item.mediaRef], this.getUIContext().getHostContext()!),
          onLoadMore: () => this.vm.loadMore(),
          onRetryLoadMore: () => this.vm.retryLoadMore()
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
