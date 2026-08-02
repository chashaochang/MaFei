import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { PageState } from "../../common/base/BaseUIState";
import { RouterConsts } from "../../common/RouterConsts";
import { EmptyView } from "../../component/EmptyView";
import { FailedView } from "../../component/FailedView";
import { VideoItem } from "../home/hometab/HomeUIState";
import { VideoCard } from "../videolist/VideoListPage";
import { SearchUIState } from "./SearchUIState";
import { SearchViewModel } from "./SearchViewModel";
import { GridView } from "@abner/refresh_v2";
import { OneMoreUtil } from "../../utils/OneMoreUtil";
import { AlertDialogV2, AdvancedDialogV2Button } from '@kit.ArkUI';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { AppThemeTokens } from "../../theme/AppThemeTokens";
import { UIConstants } from "../../common/UIConstants";
import { deviceInfo } from '@kit.BasicServicesKit';

@HMRouter({
  pageUrl: RouterConsts.SearchPage,
  useNavDst: true
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
    this.vm.cancelPendingSearch()
  }

  private primaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(nativeSurface: boolean): ResourceColor {
    return nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private nativeHeaderHeight(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT + 16
  }

  @Builder
  private searchBarContent(nativeSurface: boolean, showCancelAction: boolean) {
    Search({
      value: this.ui.searchText,
      placeholder: "搜索"
    })
      .backgroundColor(nativeSurface ? Color.Transparent : $r('app.color.bg_1'))
      .border({ width: 2, color: this.ui.focus ? $r('app.color.color_main') : Color.Transparent })
      .borderRadius(8)
      .margin({
        top: 4,
        left: 16,
        right: showCancelAction ? 0 : 16,
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
        this.vm.search(value)
      })
      .onSubmit((value: string) => {
        this.ui.searchText = value
        this.vm.search(value, true)
      })
    if (showCancelAction) {
      Text('取消')
        .fontSize(16)
        .fontColor(this.primaryTextColor(nativeSurface))
        .padding({ left: 20, right: 20 })
        .onClick(() => {
          HMRouterMgr.pop()
        })
        .height('100%')
    }
  }

  @Builder
  private nativeSearchForm() {
    Search({
      value: this.ui.searchText,
      placeholder: '搜索影片、剧集和视频'
    })
      .layoutWeight(1)
      .height(48)
      .fontColor($r('sys.color.font_primary'))
      .placeholderColor($r('sys.color.font_secondary'))
      .textFont({ size: 16 })
      .searchIcon({
        size: 20,
        color: $r('sys.color.icon_secondary')
      })
      .cancelButton({
        style: CancelButtonStyle.INPUT,
        icon: {
          size: 18,
          color: $r('sys.color.icon_secondary')
        }
      })
      .searchButton('搜索', {
        fontSize: 16,
        fontColor: $r('app.color.color_main')
      })
      .backgroundColor(Color.Transparent)
      .borderRadius(24)
      .clip(true)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive))
      .onFocus(() => {
        this.ui.focus = true
      })
      .onBlur(() => {
        this.ui.focus = false
      })
      .onChange((value: string) => {
        this.ui.searchText = value
        this.vm.search(value)
      })
      .onSubmit((value: string) => {
        this.ui.searchText = value
        this.vm.search(value, true)
      })
  }

  @Builder
  private nativeSearchHeader() {
    Row({ space: 12 }) {
      Button({ type: ButtonType.Circle, stateEffect: false }) {
        SymbolGlyph($r('sys.symbol.chevron_backward'))
          .fontSize(24)
          .fontColor([$r('sys.color.icon_primary')])
      }
      .width(48)
      .height(48)
      .buttonStyle(ButtonStyleMode.TEXTUAL)
      .backgroundColor(Color.Transparent)
      .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive))
      .accessibilityText('返回')
      .onClick(() => {
        HMRouterMgr.pop()
      })

      this.nativeSearchForm()
    }
    .width('100%')
    .height(this.nativeHeaderHeight())
    .padding({
      top: Math.max(0, this.vm.appUIState.safeTop) + 8,
      left: AppThemeTokens.pageHorizontalPadding(
        this.vm.appUIState.themeStyle, this.vm.appUIState.isBigScreen),
      right: AppThemeTokens.pageHorizontalPadding(
        this.vm.appUIState.themeStyle, this.vm.appUIState.isBigScreen),
      bottom: 8
    })
    .alignItems(VerticalAlign.Center)
    .backgroundColor(Color.Transparent)
    .zIndex(10)
  }

  @Builder
  private feiniuSearchBar() {
    Row() {
      this.searchBarContent(false, true)
    }
    .height(48)
  }

  @Builder
  private searchResultsContent(nativeSurface: boolean) {
    if (this.ui.pageState === PageState.Loading) {
      Column() {
        LoadingProgress()
          .width(40)
          .height(40)
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
    } else if (this.ui.pageState === PageState.Error) {
      FailedView()
        .onClick(() => {
          this.vm.retrySearch()
        })
    } else {
      Row() {
        if (nativeSurface) {
          Text('搜索结果')
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryTextColor(true))
          Text(this.ui.totalCount + ' 个')
            .fontSize(13)
            .fontColor(this.secondaryTextColor(true))
            .margin({ left: 8 })
        } else {
          Text(this.ui.totalCount + '个搜索结果')
            .fontSize(12)
            .fontColor(this.secondaryTextColor(false))
            .backgroundColor($r('app.color.bg_main'))
            .padding({
              left: 6,
              right: 6,
              top: 4,
              bottom: 4
            })
            .borderRadius(4)
        }
        Blank().layoutWeight(1)
      }
      .width('100%')
      .height(nativeSurface ? 56 : 46)

      GridView({
        lazyDataSource: this.vm.dataSource,
        onLazyDataSource: (dataSource) => {
          this.vm.dataSource = dataSource
        },
        itemLayout: (item, index) => this.itemLayout(item, index),
        emptyLayout: () => this.emptyLayout(),
        showEmptyLayout: this.ui.totalCount === '0',
        enableRefresh: false,
        enableLoadMore: false,
        gridAttribute: (attr) => {
          attr.scrollBar = BarState.Off
          this.ui.gridAttr = attr
          OneMoreUtil.setResponsiveGridColumnsAttr(this.vm.appUIState, this.ui.gridAttr)
        },
        lazyCachedCount: 10
      })
        .width('100%')
        .height('100%')
    }
  }

  @Builder
  private nativeSearchResults() {
    Column() {
      this.searchResultsContent(true)
    }
    .width('100%')
    .layoutWeight(1)
  }

  @Builder
  private feiniuSearchResults() {
    Column() {
      this.searchResultsContent(false)
    }
    .width('100%')
    .layoutWeight(1)
    .backgroundColor($r('app.color.bg_1'))
    .borderRadius({
      topLeft: 20,
      topRight: 20
    })
    .padding({ left: 16, right: 16 })
  }

  @Builder
  private searchHistoryContent(nativeSurface: boolean) {
    Row() {
      Text('搜索历史')
        .fontSize(nativeSurface ? 20 : 14)
        .fontWeight(nativeSurface ? FontWeight.Bold : FontWeight.Normal)
        .fontColor(this.primaryTextColor(nativeSurface))
      Blank().layoutWeight(1)
      Text('清除')
        .fontSize(14)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .onClick(() => {
          let uiContext: UIContext = this.getUIContext();
          if (deviceInfo.sdkApiVersion >= 26) {
            uiContext.getPromptAction().openCustomDialog({
              builder: () => {
                this.dialogBuilder();
              },
              systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
            }).catch(() => {
            });
          } else {
            uiContext.getPromptAction().openCustomDialog({
              builder: () => {
                this.dialogBuilder();
              }
            }).catch(() => {
            });
          }
        })
    }
    .height(nativeSurface ? 56 : 46)
    .alignItems(VerticalAlign.Center)

    Flex({ wrap: FlexWrap.Wrap }) {
      ForEach(this.ui.histories, (item: string) => {
        if (nativeSurface) {
          Button({ type: ButtonType.Capsule, stateEffect: false }) {
            Text(item)
              .fontSize(14)
              .fontColor($r('sys.color.font_primary'))
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          .height(36)
          .padding({ left: 14, right: 14 })
          .margin({ right: 10, bottom: 10 })
          .buttonStyle(ButtonStyleMode.TEXTUAL)
          .backgroundColor(Color.Transparent)
          .systemMaterial(AppThemeSurfaceResolver.material(AppThemeMaterialRole.HomeLibraryChip))
          .onClick(() => {
            this.ui.searchText = item
            this.vm.search(item)
          })
        } else {
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
        }
      })
    }
  }

  @Builder
  private nativeSearchHistory() {
    Column() {
      this.searchHistoryContent(true)
    }
    .width('100%')
    .layoutWeight(1)
    .backgroundColor(Color.Transparent)
  }

  @Builder
  private feiniuSearchHistory() {
    Column() {
      this.searchHistoryContent(false)
    }
    .width('100%')
    .height('100%')
    .padding({ left: 16, right: 16 })
    .backgroundColor($r('app.color.bg_main'))
    .borderRadius({
      topLeft: 20,
      topRight: 20
    })
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    if (showLegacyActionBar) {
      Column({ space: 10 }) {
        this.feiniuSearchBar()

        if (this.ui.searchText.trim() != '') {
          this.feiniuSearchResults()
        } else if (this.ui.histories.length > 0) {
          this.feiniuSearchHistory()
        }
      }
      .width('100%')
      .height('100%')
      .backgroundColor($r('app.color.bg_main'))
      .padding({
        top: this.vm.appUIState.safeTop,
        bottom: this.vm.appUIState.safeBottom
      })
    } else {
      Stack({ alignContent: Alignment.Top }) {
        Column() {
          if (this.ui.searchText.trim() != '') {
            this.nativeSearchResults()
          } else if (this.ui.histories.length > 0) {
            this.nativeSearchHistory()
          }
        }
        .width('100%')
        .height('100%')
        .padding({
          left: AppThemeTokens.pageHorizontalPadding(
            this.vm.appUIState.themeStyle, this.vm.appUIState.isBigScreen),
          right: AppThemeTokens.pageHorizontalPadding(
            this.vm.appUIState.themeStyle, this.vm.appUIState.isBigScreen),
          top: this.nativeHeaderHeight(),
          bottom: this.vm.appUIState.safeBottom
        })

        this.nativeSearchHeader()
      }
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
    }
  }

  build() {
    AppRouteDestination({
      title: '搜索',
      titleBarVisible: false,
      contentExtendsUnderTitleBar: true,
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
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
