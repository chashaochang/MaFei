import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { PageState } from "../../common/base/BaseUIState";
import { RouterConsts } from "../../common/RouterConsts";
import { EmptyView } from "../../component/EmptyView";
import { FailedView } from "../../component/FailedView";
import { VideoItem } from "../home/hometab/HomeUIState";
import { VideoCard } from "../videolist/VideoListPage";
import { SearchUIState } from "./SearchUIState";
import { SearchViewModel } from "./SearchViewModel";
import { AlertDialogV2, AdvancedDialogV2Button } from '@kit.ArkUI';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { AppThemeTokens } from "../../theme/AppThemeTokens";
import { UIConstants } from "../../common/UIConstants";
import { toMediaRouteRefData } from "../../media/navigation/MediaRouteRef";
import { MediaRefKind } from "../../media/MediaRef";
import { BreakpointTypeEnum } from '../../utils/BreakpointSystem';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct SearchDeleteHistoryDialog {
  @Event onDelete: () => void = () => {}

  build() {
    AlertDialogV2({
      primaryTitle: '删除确认',
      content: '确定删除历史记录吗?',
      primaryButton: new AdvancedDialogV2Button({
        content: '取消',
        action: () => {}
      }),
      secondaryButton: new AdvancedDialogV2Button({
        content: '删除',
        role: ButtonRole.ERROR,
        action: this.onDelete
      })
    })
  }
}

@ComponentV2
struct SearchResultGridItem {
  @Require @Param item: VideoItem
  @Event onOpen: () => void = () => {}

  build() {
    Column() {
      VideoCard({
        item: this.item,
        onOpen: this.onOpen
      })
    }
    .width('100%')
  }
}

@ComponentV2
struct SearchResultsSection {
  @Require @Param ui: SearchUIState
  @Require @Param vm: SearchViewModel
  @Param nativeSurface: boolean = false

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private columnsTemplate(): string {
    switch (this.vm.appUIState.currentBreakpoint) {
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

  private columnsGap(): number {
    return this.vm.appUIState.currentBreakpoint === BreakpointTypeEnum.XS ? 12 : 16
  }

  private rowsGap(): number {
    return this.vm.appUIState.currentBreakpoint === BreakpointTypeEnum.XS ? 18 : 20
  }

  private gridHorizontalPadding(): number {
    if (this.vm.appUIState.currentBreakpoint === BreakpointTypeEnum.XS) {
      return 8
    }
    if (this.vm.appUIState.currentBreakpoint === BreakpointTypeEnum.SM) {
      return 12
    }
    return 0
  }

  private openItem(item: VideoItem): void {
    HMRouterMgr.to(RouterConsts.VideoDetailPage)
      .withParam({
        id: item.id,
        ref: item.mediaRef ? toMediaRouteRefData(item.mediaRef) : undefined,
        mediaName: item.mediaName,
        isTvShows: item.kind === MediaRefKind.Series ||
          item.mediaRef?.kind === MediaRefKind.Series || item.type === 'Series'
      })
      .push()
    this.vm.saveHistory(this.ui.searchText)
  }

  build() {
    if (this.ui.pageState === PageState.Loading) {
      Column() {
        LoadingProgress().width(40).height(40)
      }
      .width('100%')
      .height('100%')
      .justifyContent(FlexAlign.Center)
    } else if (this.ui.pageState === PageState.Error) {
      FailedView().onClick(() => this.vm.retrySearch())
    } else {
      Row() {
        if (this.nativeSurface) {
          Text('搜索结果')
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryTextColor())
          Text(`${this.ui.totalCount} 个`)
            .fontSize(13)
            .fontColor(this.secondaryTextColor())
            .margin({ left: 8 })
        } else {
          Text(`${this.ui.totalCount}个搜索结果`)
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .backgroundColor($r('app.color.bg_main'))
            .padding({ left: 6, right: 6, top: 4, bottom: 4 })
            .borderRadius(4)
        }
        Blank().layoutWeight(1)
      }
      .width('100%')
      .height(this.nativeSurface ? 56 : 46)

      if (this.ui.totalCount === '0') {
        EmptyView()
          .layoutWeight(1)
      } else {
        Grid() {
          LazyForEach(this.vm.dataSource, (item: Object) => {
            GridItem() {
              SearchResultGridItem({
                item: item as VideoItem,
                onOpen: () => this.openItem(item as VideoItem)
              })
            }
          })
        }
        .columnsTemplate(this.columnsTemplate())
        .columnsGap(this.columnsGap())
        .rowsGap(this.rowsGap())
        .padding({
          left: this.gridHorizontalPadding(),
          right: this.gridHorizontalPadding()
        })
        .cachedCount(10)
        .scrollBar(BarState.Off)
        .layoutWeight(1)
      }
    }
  }
}

@ComponentV2
struct SearchHistorySection {
  @Require @Param ui: SearchUIState
  @Require @Param vm: SearchViewModel
  @Param nativeSurface: boolean = false

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  // CustomBuilderAdapter: search-delete-history-dialog
  @Builder
  private deleteHistoryDialogBuilder() {
    SearchDeleteHistoryDialog({ onDelete: () => this.vm.clearSearchHistory() })
  }

  private openClearDialog(): void {
    if (this.vm.appUIState.systemMaterialAvailable) {
      this.getUIContext().getPromptAction().openCustomDialog({
        builder: () => {
          this.deleteHistoryDialogBuilder()
        },
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).catch(() => {})
    } else {
      this.getUIContext().getPromptAction().openCustomDialog({
        builder: () => {
          this.deleteHistoryDialogBuilder()
        }
      }).catch(() => {})
    }
  }

  build() {
    Column() {
      Row() {
      Text('搜索历史')
        .fontSize(this.nativeSurface ? 20 : 14)
        .fontWeight(this.nativeSurface ? FontWeight.Bold : FontWeight.Normal)
        .fontColor(this.primaryTextColor())
      Blank().layoutWeight(1)
      Text('清除')
        .fontSize(14)
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
        .onClick(() => this.openClearDialog())
    }
    .height(this.nativeSurface ? 56 : 46)
    .alignItems(VerticalAlign.Center)

      Flex({ wrap: FlexWrap.Wrap }) {
        ForEach(this.ui.histories, (item: string) => {
        if (this.nativeSurface) {
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
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.HomeLibraryChip,
            true,
            this.vm.appUIState.systemMaterialAvailable
          ))
          .onClick(() => {
            this.ui.searchText = item
            this.vm.search(item)
          })
        } else {
          Text(item)
            .fontSize(12)
            .padding({ left: 12, right: 12, top: 6, bottom: 6 })
            .margin({ top: 10, right: 10 })
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
  }
}

@ComponentV2
export struct SearchPageContent {
  @Param showLegacyActionBar: boolean = false
  @Param embedded: boolean = false
  @Local ui: SearchUIState = new SearchUIState(this.getUIContext())
  @Local vm: SearchViewModel = new SearchViewModel(this.ui)

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
    if (this.embedded) {
      return 64
    }
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT + 16
  }











  build() {
    if (this.showLegacyActionBar) {
      Column({ space: 10 }) {
        Row() {
          Search({
            value: this.ui.searchText,
            placeholder: "搜索"
          })
            .backgroundColor((false) ? Color.Transparent : $r('app.color.bg_1'))
            .border({ width: 2, color: this.ui.focus ? $r('app.color.color_main') : Color.Transparent })
            .borderRadius(8)
            .margin({
              top: 4,
              left: 16,
              right: this.embedded ? 16 : 0,
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
          if (!this.embedded) {
            Text('取消')
              .fontSize(16)
              .fontColor(this.primaryTextColor((false)))
              .padding({ left: 20, right: 20 })
              .onClick(() => {
                HMRouterMgr.pop()
              })
              .height('100%')
          }
        }
        .height(48)

        if (this.ui.searchText.trim() != '') {
          Column() {
            SearchResultsSection({
              nativeSurface: false,
              ui: this.ui,
              vm: this.vm
            })
          }
          .width('100%')
          .layoutWeight(1)
          .backgroundColor($r('app.color.bg_1'))
          .borderRadius({
            topLeft: 20,
            topRight: 20
          })
          .padding({ left: 16, right: 16 })
        } else if (this.ui.histories.length > 0) {
          Column() {
            SearchHistorySection({
              nativeSurface: false,
              ui: this.ui,
              vm: this.vm
            })
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
      }
      .width('100%')
      .height('100%')
      .backgroundColor($r('app.color.bg_main'))
      .padding({
        top: this.embedded ? 0 : this.vm.appUIState.safeTop,
        bottom: this.vm.appUIState.safeBottom
      })
    } else {
      Stack({ alignContent: Alignment.Top }) {
        Column() {
          if (this.ui.searchText.trim() != '') {
            Column() {
              SearchResultsSection({
                nativeSurface: true,
                ui: this.ui,
                vm: this.vm
              })
            }
            .width('100%')
            .layoutWeight(1)
          } else if (this.ui.histories.length > 0) {
            Column() {
              SearchHistorySection({
                nativeSurface: true,
                ui: this.ui,
                vm: this.vm
              })
            }
            .width('100%')
            .layoutWeight(1)
            .backgroundColor(Color.Transparent)
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

        Row({ space: 12 }) {
          if (!this.embedded) {
            Button({ type: ButtonType.Circle, stateEffect: false }) {
              SymbolGlyph($r('sys.symbol.chevron_backward'))
                .fontSize(24)
                .fontColor([$r('sys.color.icon_primary')])
            }
            .width(48)
            .height(48)
            .buttonStyle(ButtonStyleMode.TEXTUAL)
            .backgroundColor(Color.Transparent)
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.AdaptiveControl,
              true,
              this.vm.appUIState.systemMaterialAvailable
            ))
            .accessibilityText('返回')
            .onClick(() => {
              HMRouterMgr.pop()
            })
          }

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
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.AdaptiveControl,
              true,
              this.vm.appUIState.systemMaterialAvailable
            ))
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
        .width('100%')
        .height(this.nativeHeaderHeight())
        .padding({
          top: this.embedded ? 8 : Math.max(0, this.vm.appUIState.safeTop) + 8,
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
      .width('100%')
      .height('100%')
      .backgroundColor(Color.Transparent)
    }
  }

}

@HMRouter({
  pageUrl: RouterConsts.SearchPage,
  useNavDst: true
})
@ComponentV2
export struct SearchPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: '搜索',
        titleBarVisible: false,
        contentExtendsUnderTitleBar: true
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              SearchPageContent({ showLegacyActionBar: false })
            } else {
              SearchPageContent({ showLegacyActionBar: true })
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
        SearchPageContent({ showLegacyActionBar: true })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
