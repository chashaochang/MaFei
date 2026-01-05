import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { RouterConsts } from "../../common/RouterConsts";
import { EmptyView } from "../../component/EmptyView";
import { VideoItem } from "../home/hometab/HomeUIState";
import { VideoCard } from "../videolist/VideoListPage";
import { SearchUIState } from "./SearchUIState";
import { SearchViewModel } from "./SearchViewModel";
import { GridView } from "@abner/refresh_v2";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { AlertDialogV2, AdvancedDialogV2Button } from '@kit.ArkUI';

@HMRouter({
  pageUrl: RouterConsts.SearchPage
})
@ComponentV2
export struct SearchPage {
  @Local ui: SearchUIState = new SearchUIState(this.getUIContext())
  @Local vm: SearchViewModel = new SearchViewModel(this.ui)

  @Monitor('vm.appUIState.currentBreakpoint')
  onBreakpointChange() {
    if (this.ui.gridAttr) {
      OneMoreUtil.setResponsiveGridColumnsAttr(this.vm.appUIState, this.ui.gridAttr)
    }
  }

  aboutToAppear(): void {
    this.vm.getSearchHistories()
  }

  aboutToDisappear(): void {
    if (this.ui.searchText.trim() != '') {
      this.vm.saveHistory(this.ui.searchText)
    }
  }

  build() {
    Column({ space: 10 }) {
      Row() {
        Search({
          value: this.ui.searchText,
          placeholder: "搜索"
        })
          .backgroundColor($r('app.color.bg_1'))
          .border({ width: 2, color: this.ui.focus ? $r('app.color.color_main') : Color.Transparent })
          .borderRadius(8)
          .margin({
            top: 4,
            left: 16,
            bottom: 4
          })
          .layoutWeight(1)
          .height(40)
          .onFocus(() => {
            this.ui.focus = true
          })
          .onBlur(() => {
            this.ui.focus = false
          })
          .onChange((value: string) => {
            this.ui.searchText = value
            if (value.trim() == '') {
              this.ui.searchResults = []
            }
            this.vm.search(value)
          })
          .onSubmit((value: string) => {
            this.ui.searchText = value
            if (value.trim() == '') {
              this.ui.searchResults = []
            }
            this.vm.search(value, true)
          })
        Text('取消')
          .fontSize(16)
          .fontColor($r('app.color.text_primary'))
          .padding({ left: 20, right: 20 })
          .onClick(() => {
            HMRouterMgr.pop()
          })
          .height('100%')
      }.height(48)


      if (this.ui.searchText.trim() != '') {
        Column() {
          Row() {
            Text(this.ui.totalCount + '个搜索结果')
              .fontSize(12)
              .fontColor($r('app.color.text_1'))
              .backgroundColor($r('app.color.bg_main'))
              .padding({
                left: 6,
                right: 6,
                top: 4,
                bottom: 4
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
            emptyLayout: () => this.emptyLayout(),
            showEmptyLayout: this.ui.totalCount === '0',
            enableRefresh: false,
            enableLoadMore: false,
            gridAttribute: (attr) => {
              attr.scrollBar = BarState.Off
              this.ui.gridAttr = attr
              OneMoreUtil.setResponsiveGridColumnsAttr(this.vm.appUIState, this.ui.gridAttr)
            },
            lazyCachedCount: 10 //缓存
          }).width('100%')
            .height('100%')
        }
        .width('100%')
        .layoutWeight(1)
        .backgroundColor($r('app.color.bg_1'))
        .borderRadius({
          topLeft: 20,
          topRight: 20
        })
        .padding({
          left: 16, right: 16
        })
      } else if (this.ui.histories.length > 0) {
        Column() {
          Row() {
            Text('搜索历史')
              .fontSize(14)
              .fontColor($r('app.color.text_primary'))
            Blank().layoutWeight(1)
            Text('清除')
              .fontSize(14)
              .fontColor($r('app.color.text_2'))
              .onClick(() => {
                let uiContext: UIContext = this.getUIContext();
                uiContext.getPromptAction().openCustomDialog({
                  builder: () => {
                    this.dialogBuilder();
                  },
                }).catch(() => {

                });
              })
          }.padding({
            top: 12,
          })

          Flex({ wrap: FlexWrap.Wrap }) {
            ForEach(this.ui.histories, (item: string) => {
              Text(item)
                .fontSize(12)
                .padding({
                  left: 12,
                  right: 12,
                  top: 6,
                  bottom: 6
                })
                .margin({
                  top: 10,
                  right: 10
                })
                .fontColor($r('app.color.text_2'))
                .backgroundColor($r('app.color.bg_1'))
                .borderRadius(8)
                .onClick(() => {
                  this.ui.searchText = item
                  this.vm.search(item)
                })
            })
          }
        }.width('100%')
        .height('100%')
        .padding({
          left: 16, right: 16
        })
        .backgroundColor($r('app.color.bg_main'))
        .borderRadius({
          topLeft: 20,
          topRight: 20
        })
      }
    }.width('100%')
    .height('100%')
    .backgroundColor($r('app.color.bg_main'))
    .padding({
      top: this.vm.appUIState.safeTop,
      bottom: this.vm.appUIState.safeBottom
    })
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
        this.vm.saveHistory(this.ui.searchText)
      })
  }

  @Builder
  emptyLayout() {
    EmptyView()
  }

  @Builder
  dialogBuilder(): void {
    AlertDialogV2({
      primaryTitle: '删除确认',
      content: '确定删除历史记录吗?',
      primaryButton: new AdvancedDialogV2Button({
        content: '取消',
        action: () => {

        },
      }),
      secondaryButton: new AdvancedDialogV2Button({
        content: '删除',
        role: ButtonRole.ERROR,
        action: () => {
          this.vm.clearSearchHistory()
        }
      }),
    })
  }
}