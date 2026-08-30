import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { PageState } from '../../common/base/BaseUIState';
import { HORIZONTAL_CARD_SPACE } from '../../common/OneMoreConst';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { EmptyView } from '../../component/EmptyView';
import { FailedView } from '../../component/FailedView';
import { SkeletonGridView } from '../../component/skeleton/SkeletonGridView';
import { sortOptions } from '../../entity/SortOption';
import { BreakpointTypeEnum } from '../../utils/BreakpointSystem';
import { VideoItem } from '../home/hometab/HomeUIState';
import { VideoCard } from '../videolist/VideoListPage';
import { FavoriteListUIState } from './FavoriteListUIState';
import { FavoriteListViewModel } from './FavoriteListViewModel';
import { HomeInsetsPolicy } from '../home/HomeInsetsPolicy';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { MediaRefKind } from '../../media/MediaRef';
import { toMediaRouteRefData } from '../../media/navigation/MediaRouteRef';
import { HdsNavDestination, HdsNavDestinationAttribute, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'
import { HomeContentLayoutPolicy } from '../home/HomeContentLayoutPolicy'

@ComponentV2
struct FavoriteSortRow {
  @Require @Param label: ResourceStr
  @Require @Param totalCount: string
  @Param descending: boolean = true
  @Param nativeSurface: boolean = false
  @Require @Param primaryTextColor: ResourceColor
  @Event onOpenSort: () => void = () => {}
  @Event onToggleDirection: () => void = () => {}

  build() {
    Row() {
      Text(this.label)
        .fontSize(14)
        .fontColor(this.primaryTextColor)
        .onClick(this.onOpenSort)
      Image(this.descending ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
        .width(16)
        .height(16)
        .fillColor(this.primaryTextColor)
        .margin({ left: 6 })
        .onClick(this.onToggleDirection)
      Text(this.totalCount)
        .fontSize(12)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1'))
        .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_1'))
        .padding({ left: 6, right: 6, top: 4, bottom: 4 })
        .margin({ left: 12 })
        .borderRadius(4)
      Blank().layoutWeight(1)
    }
    .width('100%')
    .height(46)
  }
}

@ComponentV2
struct FavoriteGridItem {
  @Require @Param item: VideoItem

  private open(): void {
    HMRouterMgr.to(RouterConsts.VideoDetailPage)
      .withParam({
        id: this.item.id,
        ref: this.item.mediaRef ? toMediaRouteRefData(this.item.mediaRef) : undefined,
        mediaName: this.item.mediaName,
        isTvShows: this.item.kind === MediaRefKind.Series ||
          this.item.mediaRef?.kind === MediaRefKind.Series || this.item.type === 'Series'
      })
      .push()
  }

  build() {
    Column() {
      VideoCard({
        item: this.item,
        onOpen: () => this.open()
      })
    }
    .width('100%')
  }
}

@ComponentV2
struct FavoriteGridHeader {
  @Require @Param topInset: number
  @Require @Param label: ResourceStr
  @Require @Param totalCount: string
  @Require @Param descending: boolean
  @Require @Param primaryTextColor: ResourceColor
  @Event onOpenSort: () => void = () => {}
  @Event onToggleDirection: () => void = () => {}

  build() {
    Column() {
      Blank().height(this.topInset)
      FavoriteSortRow({
        label: this.label,
        totalCount: this.totalCount,
        descending: this.descending,
        nativeSurface: true,
        primaryTextColor: this.primaryTextColor,
        onOpenSort: this.onOpenSort,
        onToggleDirection: this.onToggleDirection
      })
    }
    .width('100%')
  }
}

@ComponentV2
struct FavoriteListContent {
  //首页来的要隐藏顶部栏
  @Param fromHome: boolean = false
  @Param useHomeContentWidth: boolean = false
  @Param showLegacyActionBar: boolean = false
  @Param contentTopInset: number = 0
  @Param contentBottomInset?: number = undefined
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: FavoriteListUIState = new FavoriteListUIState(this.getUIContext())
  vm: FavoriteListViewModel = new FavoriteListViewModel(this.ui)

  private scrollTail(): number {
    return HomeInsetsPolicy.scrollTail(this.contentBottomInset, this.vm.appUIState.safeBottom)
  }

  private ownedContentTopInset(): number {
    return this.fromHome ? Math.max(0, this.contentTopInset) : 0
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private pageBackground(): ResourceColor {
    if (this.vm.appUIState.isBigScreen) {
      return Color.Transparent
    }
    return AppThemeSurfaceResolver.routeBackground(this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private contentBreakpoint(): string {
    return HomeContentLayoutPolicy.breakpoint(this.vm.appUIState, this.useHomeContentWidth)
  }

  private columnsTemplate(): string {
    switch (this.contentBreakpoint()) {
      case BreakpointTypeEnum.XS:
        return '1fr '.repeat(2)
      case BreakpointTypeEnum.SM:
        return '1fr '.repeat(3)
      case BreakpointTypeEnum.MD:
        return '1fr '.repeat(4)
      case BreakpointTypeEnum.LG:
        return '1fr '.repeat(6)
      case BreakpointTypeEnum.XL:
        return '1fr '.repeat(8)
      case BreakpointTypeEnum.XXL:
        return '1fr '.repeat(12)
      default:
        return '1fr '.repeat(3)
    }
  }



  aboutToAppear(): void {
    this.vm.refresh()
  }







  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: this.ui.title, showBackButton: false })
          .backgroundColor($r('app.color.bg_main'))
      }
      Column() {
        if (!(this.fromHome ? this.useNativeSurface() : !this.showLegacyActionBar)) {
          FavoriteSortRow({
            label: sortOptions[this.ui.selectedSortIndex].label,
            totalCount: this.ui.totalCount,
            descending: this.ui.isDesc,
            nativeSurface: false,
            primaryTextColor: this.primaryTextColor(false),
            onOpenSort: (): void => {
              this.vm.sortDialog.show()
            },
            onToggleDirection: () => {
              if (!this.ui.isRefreshing) {
                this.ui.isDesc = !this.ui.isDesc
                this.vm.refresh()
              }
            }
          })
        }

        if (this.ui.pageState == PageState.Loading) {
          Column() {
            Blank().height(this.ownedContentTopInset())
            SkeletonGridView()
              .layoutWeight(1)
          }
          .width('100%')
          .layoutWeight(1)
        } else {
          if (this.fromHome ? this.useNativeSurface() : !this.showLegacyActionBar) {
            FavoriteGridHeader({
              topInset: this.ownedContentTopInset(),
              label: sortOptions[this.ui.selectedSortIndex].label,
              totalCount: this.ui.totalCount,
              descending: this.ui.isDesc,
              primaryTextColor: this.primaryTextColor(true),
              onOpenSort: (): void => {
                this.vm.sortDialog.show()
              },
              onToggleDirection: (): void => {
                if (!this.ui.isRefreshing) {
                  this.ui.isDesc = !this.ui.isDesc
                  this.vm.refresh()
                }
              }
            })
          }
          if (this.ui.totalCount === '0') {
            EmptyView()
              .layoutWeight(1)
          } else {
            Refresh({ refreshing: $$this.ui.isRefreshing }) {
              Grid(this.contentScroller) {
                LazyForEach(this.vm.dataSource, (item: Object) => {
                  GridItem() {
                    FavoriteGridItem({ item: item as VideoItem })
                  }
                })
              }
              .columnsTemplate(this.columnsTemplate())
              .columnsGap(HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()))
              .rowsGap(HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()))
              .cachedCount(10)
              .scrollBar(BarState.Off)
              .onReachEnd(() => this.vm.loadMore())
            }
            .onRefreshing(() => this.vm.refresh())
            .layoutWeight(1)
          }
        }
        if (this.ui.pageState == PageState.Error) {
          FailedView().onClick(() => {
            this.vm.refresh()
          })
        }
      }
      .width('100%')
      .layoutWeight(1)
      .backgroundColor((this.fromHome ? this.useNativeSurface() : !this.showLegacyActionBar) ? Color.Transparent : this.pageBackground())
      .padding({
        left: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
        right: HORIZONTAL_CARD_SPACE.getValue(this.contentBreakpoint()) * 2,
        bottom: this.scrollTail()
      })
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.fromHome ? this.pageBackground() :
      (this.showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent))
  }


}

@HMRouter({
  pageUrl: RouterConsts.FavoriteListPage,
  useNavDst: true
})
@ComponentV2
export struct FavoriteListPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Param fromHome: boolean = false
  @Param useHomeContentWidth: boolean = false
  @Param contentTopInset: number = 0
  @Param contentBottomInset?: number = undefined
  @Param contentScroller: Scroller = new Scroller()

  private routeOptions(): AppRouteDestinationOptions {
    return {
          title: '收藏'
        }
  }

  build() {
    if (this.fromHome) {
      FavoriteListContent({
        fromHome: true,
        useHomeContentWidth: this.useHomeContentWidth,
        showLegacyActionBar: false,
        contentTopInset: this.contentTopInset,
        contentBottomInset: this.contentBottomInset,
        contentScroller: this.contentScroller
      })
    } else {
      if (this.routeDestination.usesHdsDestination()) {
        HdsNavDestination() {
          Column() {
            Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
            Stack() {
              if (this.routeDestination.usesNativeChrome()) {
                FavoriteListContent({
                  showLegacyActionBar: false,
                  contentScroller: this.contentScroller
                })
              } else {
                FavoriteListContent({
                  showLegacyActionBar: true,
                  contentScroller: this.contentScroller
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
          FavoriteListContent({
            showLegacyActionBar: true,
            contentScroller: this.contentScroller
          })
        }
        .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
        .gestureModifier(this.routeDestination.gestureModifier)
        .width('100%')
        .height('100%')
      }
    }
  }
}
