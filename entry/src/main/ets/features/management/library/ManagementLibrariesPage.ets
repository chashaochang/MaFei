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
import { MediaLibraryRefreshEvent } from '../../../events/Events'
import { ActionBar } from '../../../component/ActionBar'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../../theme/AppThemeOverlayPolicy'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ManagementNativePagePolicy } from '../ManagementNativePagePolicy'
import {
  ManagementLibraryItem,
  ManagementLibraryOperationResult
} from './ManagementLibraryModels'
import { ManagementLibrariesUIState } from './ManagementLibrariesUIState'
import { ManagementLibrariesViewModel } from './ManagementLibrariesViewModel'
import { HdsNavDestination, HdsNavDestinationAttribute, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

class ManagementLibraryMenuElement implements MenuElement {
  value: ResourceStr
  action: () => void
  enabled?: boolean

  constructor(value: ResourceStr, action: () => void, enabled: boolean = true) {
    this.value = value
    this.action = action
    this.enabled = enabled
  }
}

interface ManagementLibraryMenuOptions extends MenuOptions {
  systemMaterial?: Object
}

@ComponentV2
struct ManagementLibrariesContent {
  @Param showLegacyActionBar: boolean = false
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: ManagementLibrariesUIState = new ManagementLibrariesUIState(this.getUIContext())
  @Local vm: ManagementLibrariesViewModel = new ManagementLibrariesViewModel(this.ui)
  @Event onRegisterAdd: (action: () => void) => void = (_action: () => void) => {}
  @Event onRegisterScanAll: (action: () => void) => void = (_action: () => void) => {}
  @Event onScanAllEnabledChange: (enabled: boolean) => void = (_enabled: boolean) => {}
  private lifecycleOwner: IHMLifecycleOwner | null = null
  private pageShownCallback = (_context: HMLifecycleContext): void => {
    this.vm.init()
  }
  private pageHiddenCallback = (_context: HMLifecycleContext): void => {
    this.vm.stop()
  }
  private refreshEventHandler = (): void => {
    this.vm.load(true)
  }

  aboutToAppear(): void {
    this.ui.scroller = this.contentScroller
    this.onRegisterAdd(() => this.vm.openCreate())
    this.onRegisterScanAll(() => this.confirmScanAll())
    this.onScanAllEnabledChange(!this.ui.scanningAll)
    this.getUIContext().getHostContext()?.eventHub.on(MediaLibraryRefreshEvent, this.refreshEventHandler)
    this.lifecycleOwner = HMRouterMgr.getCurrentLifecycleOwner()
    if (this.lifecycleOwner) {
      this.lifecycleOwner.addObserver(HMLifecycleState.onShown, this.pageShownCallback)
      this.lifecycleOwner.addObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    } else {
      this.vm.init()
    }
  }

  @Monitor('ui.scanningAll')
  onScanningAllChange(): void {
    this.onScanAllEnabledChange(!this.ui.scanningAll)
  }

  aboutToDisappear(): void {
    this.getUIContext().getHostContext()?.eventHub.off(MediaLibraryRefreshEvent, this.refreshEventHandler)
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

  private menuMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.AppFloating,
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.systemMaterialAvailable,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  private libraryMenuOptions(): ManagementLibraryMenuOptions {
    const decision = this.menuMaterialDecision()
    if (decision === OverlayMaterialDecision.UseBlurFallback) {
      return {
        placement: Placement.BottomRight,
        backgroundColor: Color.Transparent,
        backgroundBlurStyle: BlurStyle.Thin
      }
    }
    if (decision === OverlayMaterialDecision.UseFloatingMaterial) {
      return {
        placement: Placement.BottomRight,
        backgroundColor: Color.Transparent,
        systemMaterial: AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating)
      }
    }
    if (decision === OverlayMaterialDecision.DisableSystemMaterial) {
      return {
        placement: Placement.BottomRight,
        backgroundColor: $r('app.color.bg_1'),
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }
    }
    return { placement: Placement.BottomRight }
  }

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle)
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  @Styles
  pressedLibraryRowStyle(): void {
    .backgroundColor(this.isNativeTheme() ?
      AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
      Color.Transparent)
  }

  @Styles
  normalLibraryRowStyle(): void {
    .backgroundColor(Color.Transparent)
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

  private libraryMenuItems(item: ManagementLibraryItem): MenuElement[] {
    const operationEnabled = !this.ui.scanningAll && this.ui.busyLibraryId.length === 0
    return [
      new ManagementLibraryMenuElement(
        $r('app.string.management_library_scan_one'),
        () => {
          this.vm.scanOne(item).then((result: ManagementLibraryOperationResult) =>
            this.showResult(result, $r('app.string.management_library_scan_started')))
        }, operationEnabled),
      new ManagementLibraryMenuElement(
        $r('app.string.management_library_edit'),
        () => this.vm.openEdit(item)),
      new ManagementLibraryMenuElement(
        $r('app.string.management_library_remove'),
        () => this.requestRemove(item), operationEnabled)
    ]
  }















  build() {
    Column() {
      if ((this.showLegacyActionBar)) {
        ActionBar({
          title: $r('app.string.management_library_title'),
          showBackButton: true,
          rightBtnIcon: $r('app.media.add'),
          rightBtnAccessibilityText: $r('app.string.management_library_add'),
          onRightBtnClick: () => this.vm.openCreate()
        })
      }
      if (this.ui.pageState === PageState.Loading) {
        Column() {
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
        .layoutWeight(1)
        .width(this.isNativeTheme() ? 'calc(100% - 24vp)' : '100%')
        .height('100%')
        .padding(this.isNativeTheme() ?
          { left: 12, right: 12, top: 8, bottom: this.vm.appUIState.safeBottom + 12 } : 0)
        .borderRadius(this.isNativeTheme() ?
          AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle) : 0)
        .clip(this.isNativeTheme())
        .backgroundColor(this.isNativeTheme() ?
          (this.useNativeMaterial() ? AppThemeSurfaceResolver.contentGroupBackground() :
            $r('app.color.bg_1')) : Color.Transparent)
      } else if (this.ui.pageState === PageState.Error) {
        Column({ space: 12 }) {
          SymbolGlyph(($r('sys.symbol.exclamationmark_circle')))
            .fontSize(38)
            .fontColor([$r('app.color.color_main')])
          Text((this.localizedMessage(this.ui.errorMessage)))
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
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
            .onClick(() => this.vm.load(false))
        }
        .width('calc(100% - 48vp)')
        .layoutWeight(1)
        .padding({
          left: 24,
          right: 24,
          top: 24,
          bottom: this.isNativeTheme() ? this.vm.appUIState.safeBottom + 24 : 24
        })
        .justifyContent(FlexAlign.Center)
      } else {
        Row({ space: this.isLarge() ? 12 : 0 }) {
          Column() {
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
                  .attributeModifier(AppThemeSurfaceResolver.modifier(
                    AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
                  .onClick(() => this.vm.load(true))
              }
              .width('calc(100% - 24vp)')
              .padding({ left: 12, right: 12, top: 4, bottom: 4 })
            }
            Refresh({ refreshing: $$this.ui.isRefreshing }) {
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
                    .attributeModifier(AppThemeSurfaceResolver.modifier(
                      AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
                    .onClick(() => this.vm.openCreate())
                }
                .width('100%')
                .layoutWeight(1)
                .justifyContent(FlexAlign.Center)
              } else {
                List({ scroller: this.ui.scroller }) {
                  ForEach(this.ui.items, (item: ManagementLibraryItem, index: number) => {
                    ListItem() {
                      Column() {
                        Row({ space: 12 }) {
                          SymbolGlyph($r('sys.symbol.externaldrive'))
                            .fontSize(20)
                            .fontColor([AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
                              $r('sys.color.icon_secondary') : $r('app.color.color_main')])
                          Column({ space: 3 }) {
                            Text((item).name)
                              .fontSize(16)
                              .fontWeight(FontWeight.Medium)
                              .fontColor(this.primaryTextColor())
                              .maxLines(1)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                            Text(this.pathSummary((item)))
                              .fontSize(13)
                              .fontColor(this.secondaryTextColor())
                              .maxLines(1)
                              .textOverflow({ overflow: TextOverflow.Ellipsis })
                            Text(this.scanStatus((item)))
                              .fontSize(12)
                              .fontColor(this.secondaryTextColor())
                              .maxLines(1)
                          }
                          .layoutWeight(1)
                          .alignItems(HorizontalAlign.Start)
                          if (this.isNativeTheme()) {
                            Button() {
                              SymbolGlyph($r('sys.symbol.more'))
                                .fontSize(18)
                                .fontColor([this.secondaryTextColor()])
                            }
                            .width(40)
                            .height(40)
                            .borderRadius(20)
                            .backgroundColor(Color.Transparent)
                            .attributeModifier(AppThemeSurfaceResolver.modifier(
                              AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
                            .accessibilityText($r('app.string.management_library_more_actions'))
                            .hitTestBehavior(HitTestMode.BLOCK_HIERARCHY)
                            .bindMenu(this.libraryMenuItems(((item))), this.libraryMenuOptions())
                          } else {
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
                            .bindMenu(this.libraryMenuItems(item))
                          }
                        }
                        .width('calc(100% - 20vp)')
                        .constraintSize({ minHeight: 72 })
                        .padding({ left: 12, right: 8, top: 8, bottom: 8 })
                        .stateStyles({ pressed: this.pressedLibraryRowStyle, normal: this.normalLibraryRowStyle })
                        .onClick(() => this.openLibrary((item)))
                        if (this.isNativeTheme() && index < this.ui.items.length - 1) {
                          Divider()
                            .color(AppThemeSurfaceResolver.contentDivider(
                              this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
                            .margin({ left: 44, right: 8 })
                        }
                      }
                      .width('100%')
                    }
                  }, (item: ManagementLibraryItem) => item.id)
                }
                .width('100%')
                .height('100%')
                .scrollBar(BarState.Auto)
                .cachedCount(10)
              }
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
          .width((this.isLarge() ? 320 : '100%'))
          .height('100%')
          .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
          .clip(true)
          .backgroundColor(this.useNativeMaterial() ?
            AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
          if (this.isLarge()) {
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
                .attributeModifier(AppThemeSurfaceResolver.modifier(
                  AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
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
        }
        .layoutWeight(1)
        .width(this.isLarge() ? 'calc(100% - 32vp)' : 'calc(100% - 24vp)')
        .padding({
          left: this.isLarge() ? 16 : 12,
          right: this.isLarge() ? 16 : 12,
          top: this.isLarge() ? 12 : 8,
          bottom: this.vm.appUIState.safeBottom + (this.isLarge() ? 16 : 12)
        })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor((this.showLegacyActionBar) ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementLibrariesPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementLibrariesPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly contentScroller: Scroller = new Scroller()
  @Local scanAllEnabled: boolean = true
  private addAction: () => void = () => {}
  private scanAllAction: () => void = () => {}

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_library_title'),
        menus: [{
                value: $r('app.string.management_library_add'),
                icon: $r('sys.symbol.plus'),
                action: () => this.addAction()
              }, {
                value: $r('app.string.management_library_scan_all'),
                icon: $r('sys.symbol.arrow_clockwise'),
                isEnabled: this.scanAllEnabled,
                action: () => this.scanAllAction()
              }],
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
              ManagementLibrariesContent({
                showLegacyActionBar: false,
                contentScroller: this.contentScroller,
                onRegisterAdd: (action: () => void) => this.addAction = action,
                onRegisterScanAll: (action: () => void) => this.scanAllAction = action,
                onScanAllEnabledChange: (enabled: boolean) => this.scanAllEnabled = enabled
              })
            } else {
              ManagementLibrariesContent({
                showLegacyActionBar: true,
                contentScroller: this.contentScroller,
                onRegisterAdd: (action: () => void) => this.addAction = action,
                onRegisterScanAll: (action: () => void) => this.scanAllAction = action,
                onScanAllEnabledChange: (enabled: boolean) => this.scanAllEnabled = enabled
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
        ManagementLibrariesContent({
          showLegacyActionBar: true,
          contentScroller: this.contentScroller,
          onRegisterAdd: (action: () => void) => this.addAction = action,
          onRegisterScanAll: (action: () => void) => this.scanAllAction = action,
          onScanAllEnabledChange: (enabled: boolean) => this.scanAllEnabled = enabled
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
