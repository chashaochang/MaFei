import { GridView, RefreshLayoutStatus } from '@abner/refresh_v2';
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { PageState } from '../../common/base/BaseUIState';
import { HORIZONTAL_CARD_SPACE } from '../../common/OneMoreConst';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
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

@HMRouter({
  pageUrl: RouterConsts.FavoriteListPage
})
@ComponentV2
export struct FavoriteListPage {
  //首页来的要隐藏顶部栏
  @Param fromHome: boolean = false
  @Param contentBottomInset?: number = undefined
  @Local ui: FavoriteListUIState = new FavoriteListUIState(this.getUIContext())
  vm: FavoriteListViewModel = new FavoriteListViewModel(this.ui)

  private scrollTail(): number {
    return HomeInsetsPolicy.scrollTail(this.contentBottomInset, this.vm.appUIState.safeBottom)
  }

  aboutToAppear(): void {
    this.vm.refresh()
  }

  build() {
    Column() {
      ActionBar({
        title: this.ui.title,
        showBackButton: false
      })
        .backgroundColor($r('app.color.bg_main'))
        .visibility(this.fromHome ? Visibility.None : Visibility.Visible)
      Column() {
        Row() {
          Text(sortOptions[this.ui.selectedSortIndex].label).fontSize(14).fontColor($r('app.color.text_primary'))
            .onClick(() => {
              this.vm.sortDialog.show()
            })
          Image(this.ui.isDesc ? $r('app.media.jiangxu') : $r('app.media.shengxu'))
            .width(16)
            .height(16)
            .fillColor($r('app.color.text_primary'))
            .margin({ left: 6 })
            .onClick(() => {
              if (this.vm.controller.getRefreshLayoutStatus() === RefreshLayoutStatus.Finish) {
                this.ui.isDesc = !this.ui.isDesc
                this.vm.refresh()
              }
            })
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
          Blank().layoutWeight(1)
        }
        .width('100%')
        .height(46)

        GridView({
          lazyDataSource: this.vm.dataSource,
          onLazyDataSource: (dataSource) => {
            this.vm.dataSource = dataSource
          },
          showEmptyLayout: this.ui.totalCount === '0',
          emptyLayout: () => this.emptyLayout(),
          itemLayout: (item, index) => this.itemLayout(item, index), //条目布局
          controller: this.vm.controller, //控制器，负责关闭下拉和上拉
          onRefresh: () => {
            //下拉刷新
            this.vm.refresh()
          },
          onLoadMore: () => {
            //上拉加载
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
          lazyCachedCount: 10 //缓存
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
      .backgroundColor(this.vm.appUIState.isBigScreen ? Color.Transparent : $r('app.color.bg_main'))
      // .borderRadius({
      //   topLeft: 20,
      //   topRight: 20
      // })
      .padding({
        left: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2,
        right: HORIZONTAL_CARD_SPACE.getValue(this.vm.appUIState.currentBreakpoint) * 2,
        bottom: this.scrollTail()
      })
    }
  }

  @Builder
  itemLayout(item: Object, _index: number): void {
    // 自定义item
    VideoCard({ item: item as VideoItem })
      .onClick(() => {
        HMRouterMgr.to(RouterConsts.VideoDetailPage)
          .withParam({
            id: (item as VideoItem).id,
            mediaName: (item as VideoItem).mediaName,
            isTvShows: (item as VideoItem).type === 'Series',
          })
          .push()
      })
  }

  @Builder
  emptyLayout() {
    EmptyView()
  }
}
