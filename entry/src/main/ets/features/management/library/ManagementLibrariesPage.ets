import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter'
import { PageState } from '../../../common/base/BaseUIState'
import { CustomRefreshComponent } from '../../../common/CustomRefreshComponent'
import { RouterConsts } from '../../../common/RouterConsts'
import { ActionBar } from '../../../component/ActionBar'
import { AppRouteDestination } from '../../../component/AppRouteDestination'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ManagementNativePagePolicy } from '../ManagementNativePagePolicy'
import {
  ManagementLibraryItem,
  ManagementLibraryOperationResult
} from './ManagementLibraryModels'
import { ManagementLibrariesUIState } from './ManagementLibrariesUIState'
import { ManagementLibrariesViewModel } from './ManagementLibrariesViewModel'

@HMRouter({
  pageUrl: RouterConsts.ManagementLibrariesPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementLibrariesPage {
  @Local ui: ManagementLibrariesUIState = new ManagementLibrariesUIState(this.getUIContext())
  @Local vm: ManagementLibrariesViewModel = new ManagementLibrariesViewModel(this.ui)
  private lifecycleOwner: IHMLifecycleOwner | null = null
  private pageShownCallback = (_context: HMLifecycleContext): void => {
    this.vm.init()
  }
  private pageHiddenCallback = (_context: HMLifecycleContext): void => {
    this.vm.stop()
  }

  aboutToAppear(): void {
    this.lifecycleOwner = HMRouterMgr.getCurrentLifecycleOwner()
    if (this.lifecycleOwner) {
      this.lifecycleOwner.addObserver(HMLifecycleState.onShown, this.pageShownCallback)
      this.lifecycleOwner.addObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    } else {
      this.vm.init()
    }
  }

  aboutToDisappear(): void {
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onShown, this.pageShownCallback)
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    this.lifecycleOwner = null
    this.vm.stop()
  }

  private isLarge(): boolean {
    return ManagementNativePagePolicy.isExpanded(this.vm.appUIState.deviceWidth)
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private localizedMessage(message: string): string {
    if (!message) {
      return this.getString('management_library_operation_failed')
    }
    try {
      return this.getString(message)
    } catch (error) {
      return message
    }
  }

  private pathSummary(item: ManagementLibraryItem): string {
    if (item.paths.length === 0) {
      return this.getString('management_library_no_paths')
    }
    if (item.paths.length === 1) {
      return item.paths[0]
    }
    return `${item.paths[0]} · ${item.paths.length} ${this.getString('management_library_paths_unit')}`
  }

  private scanStatus(item: ManagementLibraryItem): string {
    if (item.refreshProgress !== undefined) {
      return `${this.getString('management_library_scanning')} ${Math.round(item.refreshProgress)}%`
    }
    return item.refreshStatus.length > 0 ? item.refreshStatus :
      this.getString('management_library_ready')
  }

  private openLibrary(item: ManagementLibraryItem): void {
    this.vm.selectLibrary(item)
    if (!this.isLarge()) {
      this.vm.openEdit(item)
    }
  }

  private showResult(result: ManagementLibraryOperationResult, success: ResourceStr): void {
    this.getUIContext().getPromptAction().showToast({
      message: result.ok ? success : this.localizedMessage(result.message)
    })
  }

  private confirmScanAll(): void {
    if (this.ui.scanningAll || this.ui.busyLibraryId.length > 0) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_library_operation_in_progress')
      })
      return
    }
    this.getUIContext().getPromptAction().showDialog({
      title: $r('app.string.management_library_scan_all'),
      message: $r('app.string.management_library_scan_all_message'),
      buttons: [
        { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
        { text: $r('app.string.management_library_start_scan'), color: $r('app.color.color_main') }
      ]
    }).then((response) => {
      if (response.index !== 1) {
        return
      }
      this.vm.scanAll().then((result: ManagementLibraryOperationResult) =>
        this.showResult(result, $r('app.string.management_library_scan_started')))
    }).catch(() => {
    })
  }

  private requestRemove(item: ManagementLibraryItem): void {
    this.vm.confirmRemove(item)
    if (!this.ui.removeDialogVisible) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_library_operation_in_progress')
      })
      return
    }
    const paths = item.paths.length > 0 ? item.paths.map((path: string) => `• ${path}`).join('\n') :
      this.getString('management_library_no_paths')
    const message = `${this.getString('management_library_remove_message')} ${item.name}\n\n${paths}\n\n${
      this.getString('management_library_remove_notice')}`
    this.getUIContext().getPromptAction().showDialog({
      title: $r('app.string.management_library_remove_title'),
      message: message,
      buttons: [
        { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
        { text: $r('app.string.management_library_remove'), color: $r('app.color.main_red_color') }
      ]
    }).then((response) => {
      if (response.index !== 1) {
        this.vm.cancelRemove()
        return
      }
      this.vm.removeConfirmed().then((result: ManagementLibraryOperationResult) =>
        this.showResult(result, $r('app.string.management_library_remove_success')))
    }).catch(() => {
      this.vm.cancelRemove()
    })
  }

  @Builder
  libraryMenu(item: ManagementLibraryItem) {
    Menu() {
      MenuItem({ content: $r('app.string.management_library_scan_one') })
        .enabled(!this.ui.scanningAll && this.ui.busyLibraryId.length === 0)
        .onClick(() => {
          this.vm.scanOne(item).then((result: ManagementLibraryOperationResult) =>
            this.showResult(result, $r('app.string.management_library_scan_started')))
        })
      MenuItem({ content: $r('app.string.management_library_edit') })
        .onClick(() => this.vm.openEdit(item))
      MenuItem({ content: $r('app.string.management_library_remove') })
        .enabled(!this.ui.scanningAll && this.ui.busyLibraryId.length === 0)
        .onClick(() => this.requestRemove(item))
    }
  }

  @Builder
  libraryRow(item: ManagementLibraryItem) {
    Row({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.externaldrive'))
        .fontSize(20)
        .fontColor([$r('app.color.color_main')])
      Column({ space: 3 }) {
        Text(item.name)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.pathSummary(item))
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.scanStatus(item))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .maxLines(1)
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      Button() {
        SymbolGlyph($r('sys.symbol.more'))
          .fontSize(18)
          .fontColor([this.secondaryTextColor()])
      }
      .width(40)
      .height(40)
      .borderRadius(20)
      .backgroundColor(Color.Transparent)
      .accessibilityText($r('app.string.management_library_more_actions'))
      .bindMenu(this.libraryMenu(item))
    }
    .width('calc(100% - 20vp)')
    .constraintSize({ minHeight: 72 })
    .padding({ left: 12, right: 8, top: 8, bottom: 8 })
    .onClick(() => this.openLibrary(item))
  }

  @Builder
  loadingRows() {
    Column() {
      ForEach([0, 1, 2, 3, 4], (_index: number) => {
        Row({ space: 12 }) {
          Row()
            .width(20)
            .height(20)
            .borderRadius(4)
            .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle))
          Column({ space: 8 }) {
            Row().width('45%').height(12).borderRadius(4)
              .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle))
            Row().width('76%').height(10).borderRadius(4)
              .backgroundColor(AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle))
          }
          .layoutWeight(1)
          .alignItems(HorizontalAlign.Start)
        }
        .width('calc(100% - 24vp)')
        .constraintSize({ minHeight: 72 })
        .padding({ left: 12, right: 12 })
      })
    }
    .width('100%')
  }

  @Builder
  stateView(icon: Resource, title: ResourceStr) {
    Column({ space: 12 }) {
      SymbolGlyph(icon)
        .fontSize(38)
        .fontColor([$r('app.color.color_main')])
      Text(title)
        .fontSize(14)
        .fontColor(this.secondaryTextColor())
        .textAlign(TextAlign.Center)
        .maxLines(4)
      Button($r('app.string.management_retry'), { type: ButtonType.Normal })
        .height(40)
        .padding({ left: 20, right: 20 })
        .borderRadius(20)
        .fontColor($r('app.color.on_primary'))
        .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .onClick(() => this.vm.load(false))
    }
    .width('calc(100% - 48vp)')
    .layoutWeight(1)
    .padding(24)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  refreshBuilder() {
    CustomRefreshComponent({
      refreshOffset: this.ui.refreshOffset,
      refreshStatus: this.ui.refreshStatus,
      enableSafeTop: false
    })
  }

  @Builder
  staleBanner() {
    if (this.ui.stale) {
      Row({ space: 8 }) {
        SymbolGlyph($r('sys.symbol.exclamationmark_triangle'))
          .fontSize(16)
          .fontColor([$r('app.color.status_warning')])
        Text($r('app.string.management_library_stale'))
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .layoutWeight(1)
        Button($r('app.string.management_retry'), { type: ButtonType.Normal })
          .height(32)
          .borderRadius(16)
          .fontSize(12)
          .fontColor($r('app.color.color_main'))
          .backgroundColor(Color.Transparent)
          .onClick(() => this.vm.load(true))
      }
      .width('calc(100% - 24vp)')
      .padding({ left: 12, right: 12, top: 4, bottom: 4 })
    }
  }

  @Builder
  libraryList() {
    if (this.ui.items.length === 0) {
      Column({ space: 12 }) {
        SymbolGlyph($r('sys.symbol.list_bullet_square'))
          .fontSize(38)
          .fontColor([$r('app.color.color_main')])
        Text($r('app.string.management_library_empty'))
          .fontSize(14)
          .fontColor(this.secondaryTextColor())
        Button($r('app.string.management_library_add'), { type: ButtonType.Normal })
          .height(40)
          .padding({ left: 20, right: 20 })
          .borderRadius(20)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
          .systemMaterial(this.useNativeMaterial() ?
            AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
            AppThemeSurfaceResolver.disabledSystemMaterial())
          .onClick(() => this.vm.openCreate())
      }
      .width('100%')
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
    } else {
      List({ scroller: this.ui.scroller }) {
        ForEach(this.ui.items, (item: ManagementLibraryItem) => {
          ListItem() {
            this.libraryRow(item)
          }
        }, (item: ManagementLibraryItem) => item.id)
      }
      .width('100%')
      .height('100%')
      .scrollBar(BarState.Auto)
      .cachedCount(10)
    }
  }

  @Builder
  refreshableList() {
    Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.refreshBuilder() }) {
      this.libraryList()
    }
    .layoutWeight(1)
    .pullDownRatio(0.3)
    .refreshOffset(56)
    .maxPullDownDistance(64)
    .onStateChange((status: RefreshStatus) => {
      this.ui.refreshStatus = status
    })
    .onOffsetChange((offset: number) => {
      this.ui.refreshOffset = offset
    })
    .onRefreshing(() => this.vm.load(true))
  }

  @Builder
  listPane(width: Length) {
    Column() {
      this.staleBanner()
      this.refreshableList()
    }
    .width(width)
    .height('100%')
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }

  @Builder
  detailPane() {
    Column({ space: 16 }) {
      if (this.vm.selectedLibrary()) {
        SymbolGlyph($r('sys.symbol.externaldrive'))
          .fontSize(42)
          .fontColor([$r('app.color.color_main')])
        Text(this.vm.selectedLibrary()!.name)
          .fontSize(22)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
        Text(this.pathSummary(this.vm.selectedLibrary()!))
          .fontSize(14)
          .fontColor(this.secondaryTextColor())
          .textAlign(TextAlign.Center)
          .maxLines(4)
        Button($r('app.string.management_library_edit'), { type: ButtonType.Normal })
          .height(40)
          .padding({ left: 22, right: 22 })
          .borderRadius(20)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
          .systemMaterial(this.useNativeMaterial() ?
            AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
            AppThemeSurfaceResolver.disabledSystemMaterial())
          .onClick(() => this.vm.openEdit(this.vm.selectedLibrary()!))
      } else {
        Text($r('app.string.management_library_select_hint'))
          .fontSize(14)
          .fontColor(this.secondaryTextColor())
      }
    }
    .layoutWeight(1)
    .height('100%')
    .padding(24)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  pageBody() {
    if (this.ui.pageState === PageState.Loading) {
      this.loadingRows()
    } else if (this.ui.pageState === PageState.Error) {
      this.stateView($r('sys.symbol.exclamationmark_circle'), this.localizedMessage(this.ui.errorMessage))
    } else if (this.isLarge()) {
      Row({ space: 12 }) {
        this.listPane(320)
        this.detailPane()
      }
      .layoutWeight(1)
      .width('calc(100% - 32vp)')
      .padding({ left: 16, right: 16, top: 12, bottom: this.vm.appUIState.safeBottom + 16 })
    } else {
      Column() {
        this.listPane('100%')
      }
      .layoutWeight(1)
      .width('calc(100% - 24vp)')
      .padding({ left: 12, right: 12, top: 8, bottom: this.vm.appUIState.safeBottom + 12 })
    }
  }

  @Builder
  pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: $r('app.string.management_library_title'),
          showBackButton: true,
          rightBtnIcon: $r('app.media.add'),
          rightBtnAccessibilityText: $r('app.string.management_library_add'),
          onRightBtnClick: () => this.vm.openCreate()
        })
      }
      this.pageBody()
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_library_title'),
      menus: [{
        value: $r('app.string.management_library_add'),
        icon: $r('sys.symbol.plus'),
        action: () => this.vm.openCreate()
      }, {
        value: $r('app.string.management_library_scan_all'),
        icon: $r('sys.symbol.arrow_clockwise'),
        isEnabled: !this.ui.scanningAll,
        action: () => this.confirmScanAll()
      }],
      scrollControllers: [this.ui.scroller],
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
