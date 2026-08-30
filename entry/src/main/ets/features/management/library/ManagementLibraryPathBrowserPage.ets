import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter'
import { PageState } from '../../../common/base/BaseUIState'
import { RouterConsts } from '../../../common/RouterConsts'
import { ActionBar } from '../../../component/ActionBar'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ManagementLibraryPathEntry } from './ManagementLibraryModels'
import { ManagementLibraryPathBrowserUIState } from './ManagementLibraryPathBrowserUIState'
import { ManagementLibraryPathBrowserViewModel } from './ManagementLibraryPathBrowserViewModel'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct ManagementLibraryDirectoryRow {
  @Require @Param entry: ManagementLibraryPathEntry
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Event onOpen: (entry: ManagementLibraryPathEntry) => void = (_entry: ManagementLibraryPathEntry) => {}

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  @Styles
  pressedStyle(): void {
    .backgroundColor(this.isNativeTheme() ?
      AppThemeSurfaceResolver.interactionOverlay(this.themeStyle) : Color.Transparent)
  }

  @Styles
  normalStyle(): void {
    .backgroundColor(Color.Transparent)
  }

  build() {
    Row({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.externaldrive'))
        .fontSize(20)
        .fontColor([this.isNativeTheme() ? $r('sys.color.icon_secondary') : $r('app.color.color_main')])
      Column({ space: 3 }) {
        Text(this.entry.name)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.entry.path)
          .fontSize(12)
          .fontColor(this.secondaryTextColor())
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
      SymbolGlyph($r('sys.symbol.chevron_right'))
        .fontSize(20)
        .fontColor([$r('sys.color.icon_tertiary')])
    }
    .width('calc(100% - 24vp)')
    .constraintSize({ minHeight: 60 })
    .padding({ left: 12, right: 12, top: 8, bottom: 8 })
    .stateStyles({ pressed: this.pressedStyle, normal: this.normalStyle })
    .onClick(() => this.onOpen(this.entry))
  }
}

@ComponentV2
struct ManagementLibraryBrowserHeader {
  @Require @Param currentPath: string
  @Require @Param isValidating: boolean
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Event onGoParent: () => void = () => {}
  @Event onChoose: () => void = () => {}

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Column({ space: 10 }) {
      Row({ space: 8 }) {
        if (this.currentPath.length > 0) {
          Button() {
            SymbolGlyph($r('sys.symbol.chevron_left'))
              .fontSize(18)
              .fontColor([this.primaryTextColor()])
          }
          .width(36)
          .height(36)
          .borderRadius(18)
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
          .onClick(() => this.onGoParent())
        }
        Text(this.currentPath.length > 0 ? this.currentPath : $r('app.string.management_library_path_root'))
          .layoutWeight(1)
          .fontSize(14)
          .fontColor(this.secondaryTextColor())
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        if (this.currentPath.length > 0) {
          Button($r('app.string.management_library_path_choose'))
            .height(36)
            .padding({ left: 16, right: 16 })
            .borderRadius(18)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
            .enabled(!this.isValidating)
            .onClick(() => this.onChoose())
        }
      }
      .width('100%')
    }
    .width('calc(100% - 24vp)')
    .padding({ left: 12, right: 12, top: 12, bottom: 8 })
  }
}

@ComponentV2
struct ManagementLibraryManualPathSection {
  @Require @Param ui: ManagementLibraryPathBrowserUIState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param validationMessage: ResourceStr
  @Require @Param canReturnManualPath: boolean
  @Event onManualPathChange: (value: string) => void = (_value: string) => {}
  @Event onValidate: () => void = () => {}
  @Event onUse: () => void = () => {}

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private primaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  build() {
    Column({ space: 10 }) {
      Text($r('app.string.management_library_path_manual_title'))
        .width('100%')
        .fontSize(14)
        .fontWeight(FontWeight.Medium)
        .fontColor(this.primaryTextColor())
        .textAlign(TextAlign.Start)
      TextInput({
        text: this.ui.manualPath,
        placeholder: $r('app.string.management_library_path_placeholder')
      })
        .width('100%')
        .height(44)
        .borderRadius(12)
        .backgroundColor(this.useNativeMaterial() ?
          $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg'))
        .onChange((value: string) => this.onManualPathChange(value))
      if (this.ui.validationMessage.length > 0) {
        Text(this.validationMessage)
          .width('100%')
          .fontSize(12)
          .fontColor(this.canReturnManualPath ?
            $r('app.color.status_success') : $r('app.color.main_red_color'))
          .textAlign(TextAlign.Start)
      }
      Row({ space: 10 }) {
        Button(this.ui.isValidating ? $r('app.string.management_library_path_validating') :
          $r('app.string.management_library_path_validate'), { type: ButtonType.Normal })
          .layoutWeight(1)
          .height(40)
          .borderRadius(20)
          .backgroundColor(Color.Transparent)
          .fontColor($r('app.color.color_main'))
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
          .enabled(!this.ui.isValidating && this.ui.manualPath.trim().length > 0)
          .onClick(() => this.onValidate())
        Button($r('app.string.management_library_path_use'), { type: ButtonType.Normal })
          .layoutWeight(1)
          .height(40)
          .borderRadius(20)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
          .enabled(this.canReturnManualPath)
          .onClick(() => this.onUse())
      }
      .width('100%')
    }
    .width('calc(100% - 24vp)')
    .padding({ left: 12, right: 12, top: 10, bottom: 12 })
  }
}

@ComponentV2
struct ManagementLibraryBrowserContent {
  @Require @Param ui: ManagementLibraryPathBrowserUIState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param errorMessage: ResourceStr
  @Require @Param validationMessage: ResourceStr
  @Require @Param canReturnManualPath: boolean
  @Event onRetry: () => void = () => {}
  @Event onOpen: (entry: ManagementLibraryPathEntry) => void = (_entry: ManagementLibraryPathEntry) => {}
  @Event onGoParent: () => void = () => {}
  @Event onChoose: () => void = () => {}
  @Event onManualPathChange: (value: string) => void = (_value: string) => {}
  @Event onValidate: () => void = () => {}
  @Event onUse: () => void = () => {}

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private secondaryTextColor(): ResourceColor {
    return this.isNativeTheme() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Column() {
      if (this.ui.pageState === PageState.Loading) {
        Column({ space: 12 }) {
          LoadingProgress().width(36).height(36)
          Text($r('app.string.management_library_path_loading'))
            .fontSize(14)
            .fontColor(this.secondaryTextColor())
        }
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
      } else if (this.ui.pageState === PageState.Error) {
        Column({ space: 12 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
            .fontSize(38)
            .fontColor([$r('app.color.main_red_color')])
          Text(this.errorMessage)
            .fontSize(14)
            .fontColor(this.secondaryTextColor())
            .textAlign(TextAlign.Center)
          Button($r('app.string.management_library_path_retry'), { type: ButtonType.Normal })
            .height(40)
            .padding({ left: 20, right: 20 })
            .borderRadius(20)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
            .onClick(() => this.onRetry())
        }
        .layoutWeight(1)
        .justifyContent(FlexAlign.Center)
        .padding(24)
      } else {
        ManagementLibraryBrowserHeader({
          currentPath: this.ui.currentPath,
          isValidating: this.ui.isValidating,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          onGoParent: () => this.onGoParent(),
          onChoose: () => this.onChoose()
        })
        if (this.ui.entries.length === 0) {
          Column({ space: 10 }) {
            SymbolGlyph($r('sys.symbol.list_bullet_square'))
              .fontSize(36)
              .fontColor([$r('app.color.color_main')])
            Text($r('app.string.management_library_path_empty'))
              .fontSize(14)
              .fontColor(this.secondaryTextColor())
          }
          .layoutWeight(1)
          .justifyContent(FlexAlign.Center)
        } else {
          List({ scroller: this.ui.scroller }) {
            ForEach(this.ui.entries, (entry: ManagementLibraryPathEntry, index: number) => {
              ListItem() {
                if (this.isNativeTheme()) {
                  Column() {
                    ManagementLibraryDirectoryRow({
                      entry: entry,
                      themeStyle: this.themeStyle,
                      nativeThemeAvailable: this.nativeThemeAvailable,
                      onOpen: (selected: ManagementLibraryPathEntry) => this.onOpen(selected)
                    })
                    if (index < this.ui.entries.length - 1) {
                      Divider()
                        .color(AppThemeSurfaceResolver.contentDivider(
                          this.themeStyle, this.nativeThemeAvailable))
                        .margin({ left: 44, right: 8 })
                    }
                  }
                  .width('100%')
                } else {
                  ManagementLibraryDirectoryRow({
                    entry: entry,
                    themeStyle: this.themeStyle,
                    nativeThemeAvailable: this.nativeThemeAvailable,
                    onOpen: (selected: ManagementLibraryPathEntry) => this.onOpen(selected)
                  })
                }
              }
            }, (entry: ManagementLibraryPathEntry) => entry.path)
          }
          .width('100%')
          .layoutWeight(1)
          .scrollBar(BarState.Auto)
        }
        ManagementLibraryManualPathSection({
          ui: this.ui,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          validationMessage: this.validationMessage,
          canReturnManualPath: this.canReturnManualPath,
          onManualPathChange: (value: string) => this.onManualPathChange(value),
          onValidate: () => this.onValidate(),
          onUse: () => this.onUse()
        })
      }
    }
    .width('100%')
    .height('100%')
  }
}

@ComponentV2
struct ManagementLibraryPathPageContent {
  @Require @Param ui: ManagementLibraryPathBrowserUIState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param safeBottom: number
  @Require @Param showLegacyActionBar: boolean
  @Require @Param errorMessage: ResourceStr
  @Require @Param validationMessage: ResourceStr
  @Require @Param canReturnManualPath: boolean
  @Event onRetry: () => void = () => {}
  @Event onOpen: (entry: ManagementLibraryPathEntry) => void = (_entry: ManagementLibraryPathEntry) => {}
  @Event onGoParent: () => void = () => {}
  @Event onChoose: () => void = () => {}
  @Event onManualPathChange: (value: string) => void = (_value: string) => {}
  @Event onValidate: () => void = () => {}
  @Event onUse: () => void = () => {}

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({
          title: $r('app.string.management_library_path_title'),
          showBackButton: true
        })
      }
      Column() {
        ManagementLibraryBrowserContent({
          ui: this.ui,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          errorMessage: this.errorMessage,
          validationMessage: this.validationMessage,
          canReturnManualPath: this.canReturnManualPath,
          onRetry: () => this.onRetry(),
          onOpen: (entry: ManagementLibraryPathEntry) => this.onOpen(entry),
          onGoParent: () => this.onGoParent(),
          onChoose: () => this.onChoose(),
          onManualPathChange: (value: string) => this.onManualPathChange(value),
          onValidate: () => this.onValidate(),
          onUse: () => this.onUse()
        })
      }
      .layoutWeight(1)
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.themeStyle))
      .clip(true)
      .backgroundColor(this.useNativeMaterial() ?
        AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
    }
    .width('calc(100% - 24vp)')
    .height('100%')
    .padding({ left: 12, right: 12, top: 8, bottom: this.safeBottom + 12 })
    .backgroundColor(this.showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.themeStyle, this.nativeThemeAvailable) : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementLibraryPathBrowserPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementLibraryPathBrowserPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: ManagementLibraryPathBrowserUIState =
    new ManagementLibraryPathBrowserUIState(this.getUIContext())
  @Local vm: ManagementLibraryPathBrowserViewModel =
    new ManagementLibraryPathBrowserViewModel(this.ui)
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

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private localizedMessage(message: string): string {
    if (!message) return ''
    try {
      return this.getString(message)
    } catch (error) {
      return message
    }
  }

  private chooseCurrentPath(): void {
    this.vm.validateAndReturn(this.ui.currentPath).then((success: boolean) => {
      if (!success && this.ui.validationMessage.length > 0) {
        this.getUIContext().getPromptAction().showToast({ message: this.ui.validationMessage })
      }
    })
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_library_path_title'),
        scrollControllers: [this.ui.scroller],
        beforeBack: () => {
                if (this.ui.currentPath.length === 0) return false
                this.vm.goParent()
                return true
              }
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              ManagementLibraryPathPageContent({
                ui: this.ui,
                themeStyle: this.vm.appUIState.themeStyle,
                nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                safeBottom: this.vm.appUIState.safeBottom,
                showLegacyActionBar: false,
                errorMessage: this.localizedMessage(this.ui.errorMessage),
                validationMessage: this.localizedMessage(this.ui.validationMessage),
                canReturnManualPath: this.vm.canReturnManualPath(),
                onRetry: () => this.vm.loadRoot(),
                onOpen: (entry: ManagementLibraryPathEntry) => this.vm.open(entry),
                onGoParent: () => this.vm.goParent(),
                onChoose: () => this.chooseCurrentPath(),
                onManualPathChange: (value: string) => this.vm.setManualPath(value),
                onValidate: () => this.vm.validateManualPath(),
                onUse: () => this.vm.returnManualPath()
              })
            } else {
              ManagementLibraryPathPageContent({
                ui: this.ui,
                themeStyle: this.vm.appUIState.themeStyle,
                nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                safeBottom: this.vm.appUIState.safeBottom,
                showLegacyActionBar: true,
                errorMessage: this.localizedMessage(this.ui.errorMessage),
                validationMessage: this.localizedMessage(this.ui.validationMessage),
                canReturnManualPath: this.vm.canReturnManualPath(),
                onRetry: () => this.vm.loadRoot(),
                onOpen: (entry: ManagementLibraryPathEntry) => this.vm.open(entry),
                onGoParent: () => this.vm.goParent(),
                onChoose: () => this.chooseCurrentPath(),
                onManualPathChange: (value: string) => this.vm.setManualPath(value),
                onValidate: () => this.vm.validateManualPath(),
                onUse: () => this.vm.returnManualPath()
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
        ManagementLibraryPathPageContent({
          ui: this.ui,
          themeStyle: this.vm.appUIState.themeStyle,
          nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
          safeBottom: this.vm.appUIState.safeBottom,
          showLegacyActionBar: true,
          errorMessage: this.localizedMessage(this.ui.errorMessage),
          validationMessage: this.localizedMessage(this.ui.validationMessage),
          canReturnManualPath: this.vm.canReturnManualPath(),
          onRetry: () => this.vm.loadRoot(),
          onOpen: (entry: ManagementLibraryPathEntry) => this.vm.open(entry),
          onGoParent: () => this.vm.goParent(),
          onChoose: () => this.chooseCurrentPath(),
          onManualPathChange: (value: string) => this.vm.setManualPath(value),
          onValidate: () => this.vm.validateManualPath(),
          onUse: () => this.vm.returnManualPath()
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
