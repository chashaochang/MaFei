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
import { AppRouteDestination } from '../../../component/AppRouteDestination'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ManagementLibraryPathEntry } from './ManagementLibraryModels'
import { ManagementLibraryPathBrowserUIState } from './ManagementLibraryPathBrowserUIState'
import { ManagementLibraryPathBrowserViewModel } from './ManagementLibraryPathBrowserViewModel'

@HMRouter({
  pageUrl: RouterConsts.ManagementLibraryPathBrowserPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementLibraryPathBrowserPage {
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

  @Styles
  pressedDirectoryRowStyle(): void {
    .backgroundColor(AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) :
      Color.Transparent)
  }

  @Styles
  normalDirectoryRowStyle(): void {
    .backgroundColor(Color.Transparent)
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

  @Builder
  directoryRow(entry: ManagementLibraryPathEntry) {
    Row({ space: 12 }) {
      SymbolGlyph($r('sys.symbol.externaldrive'))
        .fontSize(20)
        .fontColor([AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
          $r('sys.color.icon_secondary') : $r('app.color.color_main')])
      Column({ space: 3 }) {
        Text(entry.name)
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .maxLines(1)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(entry.path)
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
    .stateStyles({ pressed: this.pressedDirectoryRowStyle, normal: this.normalDirectoryRowStyle })
    .onClick(() => this.vm.open(entry))
  }

  @Builder
  browserHeader() {
    Column({ space: 10 }) {
      Row({ space: 8 }) {
        if (this.ui.currentPath.length > 0) {
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
          .onClick(() => this.vm.goParent())
        }
        Text(this.ui.currentPath.length > 0 ? this.ui.currentPath :
          $r('app.string.management_library_path_root'))
          .layoutWeight(1)
          .fontSize(14)
          .fontColor(this.secondaryTextColor())
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        if (this.ui.currentPath.length > 0) {
          Button($r('app.string.management_library_path_choose'))
            .height(36)
            .padding({ left: 16, right: 16 })
            .borderRadius(18)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
            .enabled(!this.ui.isValidating)
            .onClick(() => this.chooseCurrentPath())
        }
      }
      .width('100%')
    }
    .width('calc(100% - 24vp)')
    .padding({ left: 12, right: 12, top: 12, bottom: 8 })
  }

  @Builder
  manualPathSection() {
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
        .onChange((value: string) => this.vm.setManualPath(value))
      if (this.ui.validationMessage.length > 0) {
        Text(this.localizedMessage(this.ui.validationMessage))
          .width('100%')
          .fontSize(12)
          .fontColor(this.vm.canReturnManualPath() ?
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
          .onClick(() => this.vm.validateManualPath())
        Button($r('app.string.management_library_path_use'), { type: ButtonType.Normal })
          .layoutWeight(1)
          .height(40)
          .borderRadius(20)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
          .enabled(this.vm.canReturnManualPath())
          .onClick(() => this.vm.returnManualPath())
      }
      .width('100%')
    }
    .width('calc(100% - 24vp)')
    .padding({ left: 12, right: 12, top: 10, bottom: 12 })
  }

  @Builder
  browserContent() {
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
        Text(this.localizedMessage(this.ui.errorMessage))
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
          .onClick(() => this.vm.loadRoot())
      }
      .layoutWeight(1)
      .justifyContent(FlexAlign.Center)
      .padding(24)
    } else {
      this.browserHeader()
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
              if (AppThemeTokens.isNative(this.vm.appUIState.themeStyle)) {
                Column() {
                  this.directoryRow(entry)
                  if (index < this.ui.entries.length - 1) {
                    Divider()
                      .color(AppThemeSurfaceResolver.contentDivider(
                        this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable))
                      .margin({ left: 44, right: 8 })
                  }
                }
                .width('100%')
              } else {
                this.directoryRow(entry)
              }
            }
          }, (entry: ManagementLibraryPathEntry) => entry.path)
        }
        .width('100%')
        .layoutWeight(1)
        .scrollBar(BarState.Auto)
      }
      this.manualPathSection()
    }
  }

  @Builder
  pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({
          title: $r('app.string.management_library_path_title'),
          showBackButton: true
        })
      }
      Column() {
        this.browserContent()
      }
      .layoutWeight(1)
      .width('100%')
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .clip(true)
      .backgroundColor(this.useNativeMaterial() ?
        AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
    }
    .width('calc(100% - 24vp)')
    .height('100%')
    .padding({ left: 12, right: 12, top: 8, bottom: this.vm.appUIState.safeBottom + 12 })
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_library_path_title'),
      scrollControllers: [this.ui.scroller],
      beforeBack: () => {
        if (this.ui.currentPath.length === 0) return false
        this.vm.goParent()
        return true
      },
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
