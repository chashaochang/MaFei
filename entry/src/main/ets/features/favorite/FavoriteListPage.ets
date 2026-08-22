import { GridView, RefreshLayoutStatus } from '@abner/refresh_v2';
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { PageState } from '../../common/base/BaseUIState';
import { HORIZONTAL_CARD_SPACE } from '../../common/OneMoreConst';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
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

@HMRouter({
  pageUrl: RouterConsts.FavoriteListPage,
  useNavDst: true
})
@ComponentV2
export struct FavoriteListPage {
  //首页来的要隐藏顶部栏
  @Param fromHome: boolean = false
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

  @Builder
  private nativeGridHeader() {
    Column() {
      Blank().height(this.ownedContentTopInset())
      this.sortRow(true)
    }
    .width('100%')
  }

  aboutToAppear(): void {
    this.vm.refresh()
  }

  @Builder
  private nativeCountLabel() {
    Text(this.ui.totalCount)
      .fontSize(12)
      .fontColor($r('sys.color.font_secondary'))
      .padding({
        left: 6,
        right: 6,
        top: 4,
        bottom: 4
      })
      .margin({
        left: 12
      })
      .borderRadius(4)
  }

  @Builder
  private feiniuCountLabel() {
    Text(this.ui.totalCount)
      .fontSize(12)
      .fontColor($r('app.color.text_1'))
      .backgroundColor($r('app.color.bg_1'))
      .padding({
        left: 6,
        right: 6,
        top: 4,
        bottom: 4
      })
      .margin({
        left: 12
      })
      .borderRadius(4)
  }

  @Builder
  private sortRow(nativeSurface: boolean) {
    Row() {
      Text(sortOptions[this.ui.selectedSortIndex].label)
        .fontSize(14)
        .fontColor(this.primaryTextColor(nativeSurface))
        .onClick(() => {
          this.vm.sortDialog.show()
        })
      Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
        .width(16)
        .height(16)
        .fillColor(this.primaryTextColor(nativeSurface))
        .margin({ left: 6 })
        .onClick(() => {
          if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
            this.ui.isDesc = !this.ui.isDesc
            this.vm.refresh()
          }
        })
      if (nativeSurface) {
        this.nativeCountLabel()
      } else {
        this.feiniuCountLabel()
      }
      Blank().layoutWeight(1)
    }
    .width('100%')
    .height(46)
  }

  @Builder
  private listContent(nativeSurface: boolean) {
    Column() {
      if (!nativeSurface) {
        this.sortRow(false)
      }

      GridView({
        lazyDataSource: this.vm.dataSource,
        onLazyDataSource: (dataSource) => {
          this.vm.dataSource = dataSource
        },
        showEmptyLayout: this.ui.totalCount === '0',
        emptyLayout: () => this.emptyLayout(),
        itemLayout: (item, index) => this.itemLayout(item, index),
        itemHeaderLayout: nativeSurface ? () => this.nativeGridHeader() : undefined,
        controller: this.vm.controller,
        scroller: this.contentScroller,
        onRefresh: () => {
          this.vm.refresh()
        },
        onLoadMore: () => {
          this.vm.loadMore()
        },
        gridAttribute: (attr) => {
          attr.scrollBar = BarState.Off
          switch (this.vm.appUIState.currentBreakpoint) {
            case BreakpointTypeEnum.XS:
              attr.columnsTemplate = '1fr '.repeat(2)
              break
            case BreakpointTypeEnum.SM:
              attr.columnsTemplate = '1fr '.repeat(3)
              break
            case BreakpointTypeEnum.MD:
              attr.columnsTemplate = '1fr '.repeat(4)
              break
            case BreakpointTypeEnum.LG:
              attr.columnsTemplate = '1fr '.repeat(6)
              break
            case BreakpointTypeEnum.XL:
              attr.columnsTemplate = '1fr '.repeat(8)
              break
            case BreakpointTypeEnum.XXL:
              attr.columnsTemplate = '1fr '.repeat(12)
              break
            default:
              attr.columnsTemplate = '1fr '.repeat(3)
              break
          }
          attr.columnsGap = HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint)
        },
        lazyCachedCount: 10
      }).width('100%')
        .layoutWeight(1)
      if (this.ui.pageState == PageState.Loading) {
        SkeletonGridView()
      }
      if (this.ui.pageState == PageState.Error) {
        FailedView().onClick(() => {
          this.vm.refresh()
        })
      }
    }
    .width('100%')
    .layoutWeight(1)
    .backgroundColor(nativeSurface ? Color.Transparent : this.pageBackground())
    .padding({
      left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2,
      right: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2,
      bottom: this.scrollTail()
    })
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: this.ui.title,
          showBackButton: false
        })
          .backgroundColor($r('app.color.bg_main'))
      }
      this.listContent(!showLegacyActionBar)
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? $r('app.color.bg_main') : Color.Transparent)
  }

  @Builder
  private embeddedContent() {
    Column() {
      this.listContent(this.useNativeSurface())
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.pageBackground())
  }

  build() {
    if (this.fromHome) {
      this.embeddedContent()
    } else {
      AppRouteDestination({
        title: this.ui.title,
        contentBuilder: () => {
          this.pageContent(false)
        },
        legacyContentBuilder: () => {
          this.pageContent(true)
        }
      })
    }
  }

  @Builder
  itemLayout(item: Object, _index: number): void {
    // 自定义item
    VideoCard({
      item: item as VideoItem,
      onOpen: () => {
        const videoItem = item as VideoItem
        HMRouterMgr.to(RouterConsts.VideoDetailPage)
          .withParam({
            id: videoItem.id,
            ref: videoItem.mediaRef ? toMediaRouteRefData(videoItem.mediaRef) : undefined,
            mediaName: videoItem.mediaName,
            isTvShows: videoItem.kind === MediaRefKind.Series ||
              videoItem.mediaRef?.kind === MediaRefKind.Series || videoItem.type === 'Series',
          })
          .push()
      }
    })
  }

  @Builder
  emptyLayout() {
    EmptyView()
  }
}
