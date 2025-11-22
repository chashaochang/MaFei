import { GridView, RefreshLayoutStatus } from '@abner/refresh_v2';
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { VideoItem } from '../home/hometab/HomeUIState';
import { VideoCard } from '../videolist/VideoListPage';
import { FavoriteListUIState } from './FavoriteListUIState';
import { FavoriteListViewModel } from './FavoriteListViewModel';

@HMRouter({
  pageUrl: RouterConsts.FavoriteListPage
})
@ComponentV2
export struct FavoriteListPage {
  @Local ui: FavoriteListUIState = new FavoriteListUIState(this.getUIContext())
  vm: FavoriteListViewModel = new FavoriteListViewModel(this.ui)

  aboutToAppear(): void {
    this.vm.refresh()
  }

  build() {
    Column() {
      ActionBar({
        title: this.ui.title
      }).backgroundBlurStyle(BlurStyle.BACKGROUND_THICK)
      Column() {
        Row() {
          Text('按添加日期').fontSize(14).fontColor($r('app.color.text_primary'))
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
            attr.columnsTemplate = '1fr 1fr 1fr'
            attr.columnsGap = 8
          },
          lazyCachedCount: 10 //缓存
        }).width('100%')
          .layoutWeight(1)
      }
      .width('100%')
      .layoutWeight(1)
      .backgroundColor($r('app.color.bg_main'))
      .borderRadius({
        topLeft: 20,
        topRight: 20
      })
      .padding({
        left: 16, right: 16, bottom: this.vm.appUIState.safeBottom
      })
    }.backgroundColor($r('app.color.bg_main'))
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
}