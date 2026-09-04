import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { CollectionTypeOptions } from 'sdk/src/main/ets/generated-client/models'
import { PageState } from '../../../common/base/BaseUIState'
import { RouterConsts } from '../../../common/RouterConsts'
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { AppThemeTokens } from '../../../theme/AppThemeTokens'
import { ManagementLibraryAdvancedSection } from './ManagementLibraryAdvancedSection'
import { ManagementLibraryBasicSection } from './ManagementLibraryBasicSection'
import { ManagementLibraryEditorUIState } from './ManagementLibraryEditorUIState'
import { ManagementLibraryEditorViewModel } from './ManagementLibraryEditorViewModel'
import {
  ManagementLibraryOperationCode,
  ManagementLibraryOptions,
  ManagementLibraryValidationCode
} from './ManagementLibraryModels'
import { ManagementLibraryPathsSection } from './ManagementLibraryPathsSection'
import { ManagementLibraryProviderSection } from './ManagementLibraryProviderSection'
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct ManagementLibraryEditorLoadingContent {
  @Require @Param secondaryTextColor: ResourceColor

  build() {
    Column({ space: 12 }) {
      LoadingProgress()
        .width(36)
        .height(36)
      Text($r('app.string.management_library_editor_loading'))
        .fontSize(14)
        .fontColor(this.secondaryTextColor)
    }
    .width('100%')
    .height('100%')
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct ManagementLibraryEditorErrorContent {
  @Require @Param message: ResourceStr
  @Require @Param secondaryTextColor: ResourceColor
  @Require @Param useNativeMaterial: boolean
  @Event onRetry: () => void = () => {}

  build() {
    Column({ space: 14 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(40)
        .fontColor([$r('app.color.main_red_color')])
      Text(this.message)
        .fontSize(14)
        .fontColor(this.secondaryTextColor)
        .maxLines(5)
        .textAlign(TextAlign.Center)
      Button($r('app.string.management_library_retry'), { type: ButtonType.Normal })
        .height(40)
        .padding({ left: 20, right: 20 })
        .borderRadius(20)
        .fontColor($r('app.color.on_primary'))
        .backgroundColor(this.useNativeMaterial ? Color.Transparent : $r('app.color.color_main'))
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial))
        .onClick(() => this.onRetry())
    }
    .width('calc(100% - 48vp)')
    .height('100%')
    .padding({ left: 24, right: 24 })
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
struct ManagementLibraryEditorSections {
  @Require @Param ui: ManagementLibraryEditorUIState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param validationMessage: ResourceStr
  @Event onNameChange: (value: string) => void = (_value: string) => {}
  @Event onTypeChange: (value: CollectionTypeOptions) => void = (_value: CollectionTypeOptions) => {}
  @Event onAddPath: () => void = () => {}
  @Event onRemovePath: (path: string) => void = (_path: string) => {}
  @Event onOptionsChange: (options: ManagementLibraryOptions) => void =
    (_options: ManagementLibraryOptions) => {}
  @Event onExpandedChange: (expanded: boolean) => void = (_expanded: boolean) => {}

  private isNativeTheme(): boolean {
    return AppThemeTokens.isNative(this.themeStyle)
  }

  private moveProvider(order: string[], from: number, to: number): string[] {
    if (from < 0 || to < 0 || from >= order.length || to >= order.length || from === to) {
      return order.slice()
    }
    const next = order.slice()
    const removed = next.splice(from, 1)
    if (removed.length > 0) next.splice(to, 0, removed[0])
    return next
  }

  build() {
    Column({ space: 12 }) {
      ManagementLibraryBasicSection({
        name: this.ui.draft.name,
        collectionType: this.ui.draft.collectionType,
        types: this.ui.createTypes,
        isNew: this.ui.isNew,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onNameChange: (value: string) => this.onNameChange(value),
        onTypeChange: (value: CollectionTypeOptions) => this.onTypeChange(value)
      })
      ManagementLibraryPathsSection({
        paths: this.ui.draft.paths,
        pathErrors: this.ui.pathErrors,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onAdd: () => this.onAddPath(),
        onRemove: (path: string) => this.onRemovePath(path)
      })
      ManagementLibraryProviderSection({
        options: this.ui.draft.options,
        available: this.ui.availableOptions,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onOptionsChange: (options: ManagementLibraryOptions) => this.onOptionsChange(options),
        onMoveProvider: (order: string[], from: number, to: number): string[] =>
          this.moveProvider(order, from, to)
      })
      ManagementLibraryAdvancedSection({
        options: this.ui.draft.options,
        countries: this.ui.countries,
        cultures: this.ui.cultures,
        expanded: this.ui.advancedExpanded,
        themeStyle: this.themeStyle,
        nativeThemeAvailable: this.nativeThemeAvailable,
        onExpandedChange: (expanded: boolean) => this.onExpandedChange(expanded),
        onOptionsChange: (options: ManagementLibraryOptions) => this.onOptionsChange(options)
      })
      if (this.ui.saveMessage.length > 0 ||
        this.ui.validationCode !== ManagementLibraryValidationCode.None) {
        Text(this.validationMessage)
          .fontSize(13)
          .fontColor(this.ui.saveCode === ManagementLibraryOperationCode.PartialSuccess ?
            $r('app.color.status_warning') : $r('app.color.main_red_color'))
          .maxLines(5)
          .textAlign(TextAlign.Start)
      }
      Blank().height(4)
    }
    .width(this.isNativeTheme() ? 'calc(100% - 24vp)' : 'calc(100% - 32vp)')
    .margin({
      left: this.isNativeTheme() ? 12 : 16,
      right: this.isNativeTheme() ? 12 : 16,
      top: 12,
      bottom: 12
    })
  }
}

@ComponentV2
struct ManagementLibraryEditorSaveBar {
  @Require @Param ui: ManagementLibraryEditorUIState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param safeBottom: number
  @Event onSave: () => void = () => {}

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
    Row({ space: 12 }) {
      if (this.isNativeTheme()) {
        Blank()
      } else {
        Column({ space: 2 }) {
          Text(this.ui.dirty ? $r('app.string.management_library_unsaved_state') :
            $r('app.string.management_library_saved_state'))
            .fontSize(13)
            .fontColor(this.secondaryTextColor())
          if (this.ui.saving) {
            Text($r('app.string.management_library_saving'))
              .fontSize(11)
              .fontColor(this.secondaryTextColor())
          }
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
      }
      Button() {
        Row({ space: 8 }) {
          if (this.ui.saving) LoadingProgress().width(18).height(18)
          Text(this.ui.saving ? $r('app.string.management_library_saving') :
            $r('app.string.management_library_save'))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
        }
      }
      .height(44)
      .padding({ left: 22, right: 22 })
      .borderRadius(22)
      .fontColor($r('app.color.on_primary'))
      .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
      .enabled(this.ui.dirty && !this.ui.saving)
      .onClick(() => this.onSave())
    }
    .width('calc(100% - 24vp)')
    .padding({ left: 12, right: 12, top: 8, bottom: Math.max(0, this.safeBottom) + 10 })
    .backgroundColor(this.isNativeTheme() ? Color.Transparent : $r('app.color.bg_1'))
  }
}

@ComponentV2
struct ManagementLibraryEditorContent {
  @Require @Param ui: ManagementLibraryEditorUIState
  @Require @Param themeStyle: ThemeStyle
  @Require @Param nativeThemeAvailable: boolean
  @Require @Param safeBottom: number
  @Require @Param errorMessage: ResourceStr
  @Require @Param validationMessage: ResourceStr
  @Event onRetry: () => void = () => {}
  @Event onNameChange: (value: string) => void = (_value: string) => {}
  @Event onTypeChange: (value: CollectionTypeOptions) => void = (_value: CollectionTypeOptions) => {}
  @Event onAddPath: () => void = () => {}
  @Event onRemovePath: (path: string) => void = (_path: string) => {}
  @Event onOptionsChange: (options: ManagementLibraryOptions) => void =
    (_options: ManagementLibraryOptions) => {}
  @Event onExpandedChange: (expanded: boolean) => void = (_expanded: boolean) => {}
  @Event onSave: () => void = () => {}

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
        ManagementLibraryEditorLoadingContent({ secondaryTextColor: this.secondaryTextColor() })
      } else if (this.ui.pageState === PageState.Error) {
        ManagementLibraryEditorErrorContent({
          message: this.errorMessage,
          secondaryTextColor: this.secondaryTextColor(),
          useNativeMaterial: this.useNativeMaterial(),
          onRetry: () => this.onRetry()
        })
      } else {
        Scroll(this.ui.scroller) {
          ManagementLibraryEditorSections({
            ui: this.ui,
            themeStyle: this.themeStyle,
            nativeThemeAvailable: this.nativeThemeAvailable,
            validationMessage: this.validationMessage,
            onNameChange: (value: string) => this.onNameChange(value),
            onTypeChange: (value: CollectionTypeOptions) => this.onTypeChange(value),
            onAddPath: () => this.onAddPath(),
            onRemovePath: (path: string) => this.onRemovePath(path),
            onOptionsChange: (options: ManagementLibraryOptions) => this.onOptionsChange(options),
            onExpandedChange: (expanded: boolean) => this.onExpandedChange(expanded)
          })
        }
        .layoutWeight(1)
        .width('100%')
        .scrollBar(BarState.Auto)
        .enabled(!this.ui.saving)
        ManagementLibraryEditorSaveBar({
          ui: this.ui,
          themeStyle: this.themeStyle,
          nativeThemeAvailable: this.nativeThemeAvailable,
          safeBottom: this.safeBottom,
          onSave: () => this.onSave()
        })
      }
    }
    .width('100%')
    .height('100%')
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementLibraryEditorPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementLibraryEditorPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  @Local ui: ManagementLibraryEditorUIState = new ManagementLibraryEditorUIState(this.getUIContext())
  @Local vm: ManagementLibraryEditorViewModel = new ManagementLibraryEditorViewModel(this.ui)
  private initialized: boolean = false

  aboutToAppear(): void {
    if (this.initialized) {
      this.vm.resume()
      return
    }
    this.initialized = true
    this.loadRoute()
  }

  aboutToDisappear(): void {
    this.vm.stop()
  }

  @Monitor('ui.discardConfirmVisible')
  showDiscardConfirmation(): void {
    if (!this.ui.discardConfirmVisible) return
    this.getUIContext().getPromptAction().showDialog({
      title: $r('app.string.management_library_unsaved_title'),
      message: $r('app.string.management_library_unsaved_message'),
      buttons: [
        { text: $r('app.string.management_library_keep_editing'), color: $r('app.color.text_1') },
        { text: $r('app.string.management_library_discard'), color: $r('app.color.main_red_color') }
      ]
    }).then((response) => {
      if (response.index === 1) {
        this.vm.confirmDiscard()
        HMRouterMgr.pop()
      } else {
        this.vm.cancelDiscard()
      }
    }).catch(() => this.vm.cancelDiscard())
  }

  private loadRoute(): void {
    const param = HMRouterMgr.getCurrentParam() as object
    const itemId = param ? param['libraryId'] as string : ''
    if (typeof itemId === 'string' && itemId.trim().length > 0) {
      this.vm.loadEdit(itemId.trim())
    } else {
      this.vm.loadNew()
    }
  }

  private getString(name: string): string {
    try {
      return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
    } catch (error) {
      return name
    }
  }

  private localizedMessage(message: string): string {
    if (!message) return this.getString('management_library_save_failed')
    if (message === ManagementLibraryValidationCode.NameRequired) {
      return this.getString('management_library_name_required')
    }
    if (message === ManagementLibraryValidationCode.PathRequired) {
      return this.getString('management_library_path_required')
    }
    if (message === ManagementLibraryValidationCode.DuplicatePath) {
      return this.getString('management_library_path_duplicate')
    }
    if (message === ManagementLibraryOperationCode.PermissionDenied) {
      return this.getString('management_permission_denied')
    }
    if (message === ManagementLibraryOperationCode.AuthenticationExpired) {
      return this.getString('management_library_authentication_expired')
    }
    if (message === ManagementLibraryOperationCode.Unsupported) {
      return this.getString('management_library_unsupported_option')
    }
    if (message === ManagementLibraryOperationCode.Conflict) {
      return this.getString('management_library_operation_in_progress')
    }
    if (message.startsWith('management_')) return this.getString(message)
    return message
  }

  private openPathBrowser(): void {
    HMRouterMgr.to(RouterConsts.ManagementLibraryPathBrowserPage)
      .onResult((info) => {
        const result = info.result as object
        const path = result ? result['path'] as string : ''
        if (typeof path === 'string' && path.trim().length > 0) {
          this.vm.setPaths(this.ui.draft.paths.concat([path.trim()]))
        }
      })
      .push()
  }

  private removePath(path: string): void {
    this.vm.setPaths(this.ui.draft.paths.filter((item: string): boolean => item !== path))
  }

  private async save(): Promise<void> {
    const result = await this.vm.save()
    if (result.ok) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_library_save_success')
      })
      HMRouterMgr.pop()
      return
    }
    const message = result.code === ManagementLibraryOperationCode.PartialSuccess ?
      this.getString('management_library_save_partial') : this.localizedMessage(result.message || result.code)
    this.getUIContext().getPromptAction().showToast({ message: message })
  }

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: this.ui.isNew ? $r('app.string.management_library_add') :
                $r('app.string.management_library_edit'),
        scrollControllers: [this.ui.scroller],
        beforeBack: () => this.vm.beforeBack()
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              ManagementLibraryEditorContent({
                ui: this.ui,
                themeStyle: this.vm.appUIState.themeStyle,
                nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                safeBottom: this.vm.appUIState.safeBottom,
                errorMessage: this.localizedMessage(this.ui.saveMessage || 'management_library_load_failed'),
                validationMessage: this.localizedMessage(this.ui.saveMessage || this.ui.validationCode),
                onRetry: () => this.loadRoute(),
                onNameChange: (value: string) => this.vm.setName(value),
                onTypeChange: (value: CollectionTypeOptions) => this.vm.setCollectionType(value),
                onAddPath: () => this.openPathBrowser(),
                onRemovePath: (path: string) => this.removePath(path),
                onOptionsChange: (options: ManagementLibraryOptions) => this.vm.setOptions(options),
                onExpandedChange: (expanded: boolean) => this.vm.setAdvancedExpanded(expanded),
                onSave: () => this.save()
              })
            } else {
              ManagementLibraryEditorContent({
                ui: this.ui,
                themeStyle: this.vm.appUIState.themeStyle,
                nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                safeBottom: this.vm.appUIState.safeBottom,
                errorMessage: this.localizedMessage(this.ui.saveMessage || 'management_library_load_failed'),
                validationMessage: this.localizedMessage(this.ui.saveMessage || this.ui.validationCode),
                onRetry: () => this.loadRoute(),
                onNameChange: (value: string) => this.vm.setName(value),
                onTypeChange: (value: CollectionTypeOptions) => this.vm.setCollectionType(value),
                onAddPath: () => this.openPathBrowser(),
                onRemovePath: (path: string) => this.removePath(path),
                onOptionsChange: (options: ManagementLibraryOptions) => this.vm.setOptions(options),
                onExpandedChange: (expanded: boolean) => this.vm.setAdvancedExpanded(expanded),
                onSave: () => this.save()
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
        ManagementLibraryEditorContent({
          ui: this.ui,
          themeStyle: this.vm.appUIState.themeStyle,
          nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
          safeBottom: this.vm.appUIState.safeBottom,
          errorMessage: this.localizedMessage(this.ui.saveMessage || 'management_library_load_failed'),
          validationMessage: this.localizedMessage(this.ui.saveMessage || this.ui.validationCode),
          onRetry: () => this.loadRoute(),
          onNameChange: (value: string) => this.vm.setName(value),
          onTypeChange: (value: CollectionTypeOptions) => this.vm.setCollectionType(value),
          onAddPath: () => this.openPathBrowser(),
          onRemovePath: (path: string) => this.removePath(path),
          onOptionsChange: (options: ManagementLibraryOptions) => this.vm.setOptions(options),
          onExpandedChange: (expanded: boolean) => this.vm.setAdvancedExpanded(expanded),
          onSave: () => this.save()
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
