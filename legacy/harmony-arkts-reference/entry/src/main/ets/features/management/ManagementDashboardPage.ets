import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter';
import { PageState } from '../../common/base/BaseUIState';
import { CustomRefreshComponent } from '../../common/CustomRefreshComponent';
import { RouterConsts } from '../../common/RouterConsts';
import { UIConstants } from '../../common/UIConstants';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../theme/AppThemeOverlayPolicy';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import { ManagementActivityDashboardSection } from './activity/ManagementActivityDashboardSection';
import { ManagementActivityDashboardUIState } from './activity/ManagementActivityUIState';
import { ManagementActivityDashboardViewModel } from './activity/ManagementActivityViewModel';
import { ManagementSessionsDashboardSection } from './sessions/ManagementSessionsDashboardSection';
import { ManagementSessionItem } from './sessions/ManagementSessionModels';
import { ManagementSessionsUIState } from './sessions/ManagementSessionsUIState';
import { ManagementSessionsViewModel } from './sessions/ManagementSessionsViewModel';
import { ManagementDashboardSkeleton } from './ManagementDashboardSkeleton';
import { ManagementDashboardUIState } from './ManagementDashboardUIState';
import { ManagementDashboardViewModel } from './ManagementDashboardViewModel';
import {
  ManagementPermissionState,
  ManagementSectionStatus,
  ManagementTaskItem
} from './ManagementModels';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct ManagementDashboardMetric {
  @Require @Param label: ResourceStr
  @Param value: number = 0
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param secondaryTextColor: ResourceColor

  build() {
    Column({ space: 4 }) {
      Text(this.value.toString())
        .fontSize(22)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor)
      Text(this.label)
        .fontSize(12)
        .fontColor(this.secondaryTextColor)
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Start)
  }
}

@ComponentV2
struct ManagementDashboardInlineState {
  @Require @Param message: ResourceStr
  @Param retry: boolean = false
  @Param nativeMaterial: boolean = false
  @Require @Param secondaryTextColor: ResourceColor
  @Event onRetry: () => void = () => {}

  build() {
    Row({ space: 8 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(18)
        .fontColor([this.secondaryTextColor])
      Text(this.message)
        .fontSize(13)
        .fontColor(this.secondaryTextColor)
        .layoutWeight(1)
      if (this.retry) {
        Button($r('app.string.management_retry'), { type: ButtonType.Normal })
          .height(30)
          .padding({ left: 12, right: 12 })
          .borderRadius(this.nativeMaterial ? 15 : 8)
          .fontSize(12)
          .fontColor($r('app.color.color_main'))
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveControl, this.nativeMaterial))
          .onClick(this.onRetry)
      }
    }
    .width('100%')
    .padding({ top: 10, bottom: 10 })
  }
}

@ComponentV2
struct ManagementDashboardInlineLoading {
  build() {
    Row() {
      LoadingProgress()
        .width(24)
        .height(24)
        .color($r('app.color.color_main'))
    }
    .width('100%')
    .padding({ top: 10, bottom: 10 })
    .justifyContent(FlexAlign.Center)
  }
}

@ComponentV2
export struct ManagementDashboardContent {
  @Param showLegacyActionBar: boolean = false
  @Param embeddedInHome: boolean = false
  @Local ui: ManagementDashboardUIState = new ManagementDashboardUIState(this.getUIContext())
  @Local vm: ManagementDashboardViewModel = new ManagementDashboardViewModel(this.ui)
  @Local sessionsUI: ManagementSessionsUIState = new ManagementSessionsUIState(this.getUIContext())
  @Local sessionsVM: ManagementSessionsViewModel = new ManagementSessionsViewModel(this.sessionsUI)
  @Local activityUI: ManagementActivityDashboardUIState = new ManagementActivityDashboardUIState(this.getUIContext())
  @Local activityVM: ManagementActivityDashboardViewModel = new ManagementActivityDashboardViewModel(this.activityUI)
  private permissionPopTimer: number = -1
  private lifecycleOwner: IHMLifecycleOwner | null = null

  private contentTopInset(showLegacyActionBar: boolean): number {
    if (this.embeddedInHome) {
      return 0
    }
    return showLegacyActionBar ? 0 : Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }
  private pageShownCallback = (_context: HMLifecycleContext): void => {
    this.vm.init()
    this.sessionsVM.initList()
    this.activityVM.init()
  }

  private pageHiddenCallback = (_context: HMLifecycleContext): void => {
    this.vm.stopPolling()
    this.sessionsVM.stop()
    this.activityVM.stop()
  }

  aboutToAppear(): void {
    this.lifecycleOwner = HMRouterMgr.getCurrentLifecycleOwner()
    if (this.lifecycleOwner) {
      this.lifecycleOwner.addObserver(HMLifecycleState.onShown, this.pageShownCallback)
      this.lifecycleOwner.addObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    } else {
      this.vm.init()
      this.sessionsVM.initList()
      this.activityVM.init()
    }
  }

  aboutToDisappear(): void {
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onShown, this.pageShownCallback)
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    this.lifecycleOwner = null
    this.clearPermissionPopTimer()
    this.vm.stopPolling()
    this.sessionsVM.stop()
    this.activityVM.stop()
  }

  @Monitor('ui.permissionState')
  onPermissionStateChange(): void {
    this.clearPermissionPopTimer()
    if (this.ui.permissionState !== ManagementPermissionState.Denied) {
      return
    }
    this.sessionsVM.stop()
    this.activityVM.stop()
    this.getUIContext().getPromptAction().showToast({ message: this.ui.permissionMessage })
    this.permissionPopTimer = setTimeout(() => {
      this.permissionPopTimer = -1
      HMRouterMgr.pop()
    }, 100)
  }

  private clearPermissionPopTimer(): void {
    if (this.permissionPopTimer < 0) {
      return
    }
    clearTimeout(this.permissionPopTimer)
    this.permissionPopTimer = -1
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private dangerousDialogMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.Dangerous,
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.systemMaterialAvailable,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  private progressTrackColor(): ResourceColor {
    return this.useNativeMaterial() ?
      $r('sys.color.comp_background_tertiary') : $r('app.color.component_bg')
  }

  private primaryTextColor(): ResourceColor {
    return this.useNativeMaterial() ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.useNativeMaterial() ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  @Styles
  pressedRowStyle(): void {
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) : $r('app.color.bg_hover'))
  }

  @Styles
  normalRowStyle(): void {
    .backgroundColor(Color.Transparent)
  }

  @Styles
  pressedLibraryRowStyle(): void {
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.interactionOverlay(this.vm.appUIState.themeStyle) : $r('app.color.bg_hover'))
  }

  @Styles
  normalLibraryRowStyle(): void {
    .backgroundColor(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }

  private openSessionDetail(session: ManagementSessionItem): void {
    HMRouterMgr.to(RouterConsts.ManagementSessionDetailPage)
      .withParam({ sessionId: session.id })
      .push()
  }

  private openTaskDetail(task: ManagementTaskItem): void {
    HMRouterMgr.to(RouterConsts.ManagementTaskDetailPage)
      .withParam({ task: task })
      .push()
  }

  private openUsers(): void {
    HMRouterMgr.to(RouterConsts.ManagementUsersPage).push()
  }

  private openDevices(): void {
    HMRouterMgr.to(RouterConsts.ManagementDevicesPage).push()
  }

  private openActivity(): void {
    HMRouterMgr.to(RouterConsts.ManagementActivityPage).push()
  }

  private openLibraries(): void {
    HMRouterMgr.to(RouterConsts.ManagementLibrariesPage).push()
  }

  private async refreshAll(): Promise<void> {
    await Promise.all([
      this.vm.refresh(),
      this.sessionsVM.refresh(),
      this.activityVM.refresh()
    ])
  }




























  private confirmLibraryScan(): void {
    if (this.dangerousDialogMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_scan_confirm_title'),
        message: $r('app.string.management_scan_confirm_content'),
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_confirm'), color: $r('app.color.color_main') }
        ],
        backgroundColor: this.useNativeMaterial() ?
          $r('sys.color.comp_background_primary') : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }).then((response) => {
        if (response.index === 1) {
          this.vm.startLibraryScan()
        }
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_scan_confirm_title'),
        message: $r('app.string.management_scan_confirm_content'),
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_confirm'), color: $r('app.color.color_main') }
        ]
      }).then((response) => {
        if (response.index === 1) {
          this.vm.startLibraryScan()
        }
      }).catch(() => {
      })
    }
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: $r('app.string.management_title') })
      }
      if (this.ui.pageState === PageState.Loading) {
        ManagementDashboardSkeleton({
          feiniuCardColor: $r('app.color.bg_1'),
          cardRadius: AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle),
          isBigScreen: this.vm.appUIState.isBigScreen,
          useNativeMaterial: this.useNativeMaterial()
        })
          .padding({ top: this.contentTopInset(this.showLegacyActionBar) })
          .layoutWeight(1)
      } else if (this.ui.pageState === PageState.Error) {
        Column({ space: 16 }) {
          SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
            .fontSize(44)
            .fontColor([$r('app.color.color_main')])
          Text(this.ui.permissionMessage || $r('app.string.management_permission_check_failed'))
            .fontSize(15)
            .fontColor(this.secondaryTextColor())
            .textAlign(TextAlign.Center)
            .maxLines(3)
            .constraintSize({ maxWidth: 420 })
          Button($r('app.string.management_retry'), { type: ButtonType.Normal })
            .height(40)
            .padding({ left: 24, right: 24 })
            .fontSize(14)
            .fontColor($r('app.color.on_primary'))
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
            .borderRadius(this.useNativeMaterial() ? 20 : 12)
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
            .onClick(() => this.vm.init())
        }
        .layoutWeight(1)
        .width('100%')
        .padding({
          left: 24,
          right: 24,
          top: 24 + this.contentTopInset(this.showLegacyActionBar),
          bottom: 24
        })
        .justifyContent(FlexAlign.Center)
      } else {
        Refresh({ refreshing: $$this.ui.isRefreshing }) {
          Scroll() {
            Row() {
              Column() {
                Flex({
                  direction: this.vm.appUIState.isBigScreen ? FlexDirection.Row : FlexDirection.Column,
                  alignItems: ItemAlign.Start
                }) {
                  Column({ space: 12 }) {
                    Column() {
                      Column({ space: 16 }) {
                        if ((this.ui.serverStatus === ManagementSectionStatus.Content ||
                          this.ui.serverStatus === ManagementSectionStatus.Error) && this.ui.server) {
                          Row() {
                            Column({ space: 5 }) {
                              Row({ space: 7 }) {
                                Row()
                                  .width(8)
                                  .height(8)
                                  .borderRadius(4)
                                  .backgroundColor(this.ui.server.online ? $r('app.color.status_success') :
                                    $r('app.color.main_red_color'))
                                Text(this.ui.server.online ? $r('app.string.management_online') :
                                  $r('app.string.management_offline'))
                                  .fontSize(12)
                                  .fontColor(this.secondaryTextColor())
                              }
                              Text(this.ui.server.name || $r('app.string.management_server_unknown'))
                                .fontSize(22)
                                .fontWeight(FontWeight.Bold)
                                .fontColor(this.primaryTextColor())
                                .maxLines(1)
                                .textOverflow({ overflow: TextOverflow.Ellipsis })
                              Text(`${this.ui.server.productName || 'Jellyfin'} ${this.ui.server.version || ''}`)
                                .fontSize(13)
                                .fontColor(this.secondaryTextColor())
                            }
                            .layoutWeight(1)
                            .alignItems(HorizontalAlign.Start)
                            Stack({ alignContent: Alignment.Center }) {
                              SymbolGlyph($r('sys.symbol.externaldrive'))
                                .fontSize(30)
                                .fontColor([$r('app.color.color_main')])
                            }
                              .width(48)
                              .height(48)
                              .backgroundColor(AppThemeSurfaceResolver.iconWellBackground(this.vm.appUIState.themeStyle))
                              .borderRadius(16)
                          }
                          .width('100%')
                          if (this.ui.serverStatus === ManagementSectionStatus.Error) {
                            ManagementDashboardInlineState({
                              message: $r('app.string.management_stale_result'),
                              retry: true,
                              onRetry: () => this.vm.refreshServer(),
                              nativeMaterial: this.useNativeMaterial(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                          }
                        } else if (this.ui.serverStatus === ManagementSectionStatus.Content) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_server_failed'),
                            retry: true,
                            onRetry: () => this.vm.refreshServer(),
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else if (this.ui.serverStatus === ManagementSectionStatus.Loading) {
                          ManagementDashboardInlineLoading()
                        } else if (this.ui.serverStatus === ManagementSectionStatus.Empty) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_access_no_server'),
                            retry: false,
                            onRetry: () => {},
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else if (this.ui.serverStatus === ManagementSectionStatus.Unsupported) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_section_unsupported'),
                            retry: false,
                            onRetry: () => {},
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else if (this.ui.serverStatus === ManagementSectionStatus.Error) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_server_failed'),
                            retry: true,
                            onRetry: () => this.vm.refreshServer(),
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        }

                        if ((this.ui.countsStatus === ManagementSectionStatus.Content ||
                          this.ui.countsStatus === ManagementSectionStatus.Error) && this.ui.counts) {
                          Row() {
                            ManagementDashboardMetric({
                              label: $r('app.string.management_movies'),
                              value: this.ui.counts.movies,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                            ManagementDashboardMetric({
                              label: $r('app.string.management_series'),
                              value: this.ui.counts.series,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                            ManagementDashboardMetric({
                              label: $r('app.string.management_episodes'),
                              value: this.ui.counts.episodes,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                          }
                          .width('100%')
                          if (this.ui.countsStatus === ManagementSectionStatus.Error) {
                            ManagementDashboardInlineState({
                              message: $r('app.string.management_stale_result'),
                              retry: true,
                              onRetry: () => this.vm.refreshCounts(),
                              nativeMaterial: this.useNativeMaterial(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                          }
                        } else if (this.ui.countsStatus === ManagementSectionStatus.Content) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_counts_failed'),
                            retry: true,
                            onRetry: () => this.vm.refreshCounts(),
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else if (this.ui.countsStatus === ManagementSectionStatus.Loading) {
                          ManagementDashboardInlineLoading()
                        } else if (this.ui.countsStatus === ManagementSectionStatus.Empty) {
                          Row() {
                            ManagementDashboardMetric({
                              label: $r('app.string.management_movies'),
                              value: 0,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                            ManagementDashboardMetric({
                              label: $r('app.string.management_series'),
                              value: 0,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                            ManagementDashboardMetric({
                              label: $r('app.string.management_episodes'),
                              value: 0,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                          }
                          .width('100%')
                        } else if (this.ui.countsStatus === ManagementSectionStatus.Error) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_counts_failed'),
                            retry: true,
                            onRetry: () => this.vm.refreshCounts(),
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else if (this.ui.countsStatus === ManagementSectionStatus.Unsupported) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_section_unsupported'),
                            retry: false,
                            onRetry: () => {},
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        }

                        Button(this.ui.isStartingScan ? $r('app.string.management_scan_starting') :
                          ((this.ui.isLibraryScanRunning || this.ui.isScanSubmissionLocked) ?
                            $r('app.string.management_scan_running') : $r('app.string.management_scan_library')),
                        { type: ButtonType.Normal })
                          .width('100%')
                          .height(44)
                          .fontSize(15)
                          .fontWeight(FontWeight.Medium)
                          .fontColor($r('app.color.on_primary'))
                          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
                          .borderRadius(this.useNativeMaterial() ? 22 : 12)
                          .attributeModifier(AppThemeSurfaceResolver.modifier(
                            AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
                          .enabled(!this.ui.isLibraryScanRunning && !this.ui.isStartingScan && !this.ui.isScanSubmissionLocked)
                          .opacity(this.ui.isLibraryScanRunning || this.ui.isStartingScan ||
                            this.ui.isScanSubmissionLocked ? 0.55 : 1)
                          .onClick(() => {
                            this.confirmLibraryScan()
                          })
                      }
                      .width('100%')
                    }
                    .width('100%')
                    .padding(18)
                    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                    .clip(true)
                    .backgroundColor(this.useNativeMaterial() ?
                      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                    Row({ space: 12 }) {
                      SymbolGlyph($r('sys.symbol.list_bullet_square'))
                        .fontSize(20)
                        .fontColor([$r('app.color.color_main')])
                      Text($r('app.string.management_library_title'))
                        .fontSize(16)
                        .fontWeight(FontWeight.Medium)
                        .fontColor(this.primaryTextColor())
                        .layoutWeight(1)
                      SymbolGlyph($r('sys.symbol.chevron_right'))
                        .fontSize(18)
                        .fontColor([this.secondaryTextColor()])
                    }
                    .width('100%')
                    .constraintSize({ minHeight: 56 })
                    .padding({ left: 16, right: 14 })
                    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                    .clip(true)
                    .stateStyles({ pressed: this.pressedLibraryRowStyle, normal: this.normalLibraryRowStyle })
                    .onClick(() => this.openLibraries())
                    Column() {
                      Column({ space: 12 }) {
                        Row({ space: 8 }) {
                          Text($r('app.string.management_user_title'))
                            .fontSize(18)
                            .fontWeight(FontWeight.Bold)
                            .fontColor(this.primaryTextColor())
                            .layoutWeight(1)
                            .maxLines(2)
                          SymbolGlyph($r('sys.symbol.chevron_right'))
                            .fontSize(13)
                            .fontColor([this.secondaryTextColor()])
                        }
                        .width('100%')
                        .stateStyles({ pressed: this.pressedRowStyle, normal: this.normalRowStyle })
                        .onClick(() => this.openUsers())
                        if ((this.ui.usersStatus === ManagementSectionStatus.Content ||
                          this.ui.usersStatus === ManagementSectionStatus.Error) && this.ui.users) {
                          Row() {
                            ManagementDashboardMetric({
                              label: $r('app.string.management_user_summary_total'),
                              value: this.ui.users.total,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                            ManagementDashboardMetric({
                              label: $r('app.string.management_user_summary_administrators'),
                              value: this.ui.users.administrators,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                            ManagementDashboardMetric({
                              label: $r('app.string.management_user_summary_disabled'),
                              value: this.ui.users.disabled,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                          }
                          .width('100%')
                          if (this.ui.usersStatus === ManagementSectionStatus.Error) {
                            ManagementDashboardInlineState({
                              message: $r('app.string.management_stale_result'),
                              retry: true,
                              onRetry: () => this.vm.refreshUsers(),
                              nativeMaterial: this.useNativeMaterial(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                          }
                        } else if (this.ui.usersStatus === ManagementSectionStatus.Loading) {
                          ManagementDashboardInlineLoading()
                        } else if (this.ui.usersStatus === ManagementSectionStatus.Error) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_user_load_failed'),
                            retry: true,
                            onRetry: () => this.vm.refreshUsers(),
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else if (this.ui.usersStatus === ManagementSectionStatus.Unsupported) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_section_unsupported'),
                            retry: false,
                            onRetry: () => {},
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else {
                          Row() {
                            ManagementDashboardMetric({
                              label: $r('app.string.management_user_summary_total'),
                              value: 0,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                            ManagementDashboardMetric({
                              label: $r('app.string.management_user_summary_administrators'),
                              value: 0,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                            ManagementDashboardMetric({
                              label: $r('app.string.management_user_summary_disabled'),
                              value: 0,
                              primaryTextColor: this.primaryTextColor(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                          }
                          .width('100%')
                        }
                      }
                      .width('100%')
                    }
                    .width('100%')
                    .padding(16)
                    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                    .clip(true)
                    .backgroundColor(this.useNativeMaterial() ?
                      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                    Column() {
                      Column({ space: 10 }) {
                        Row() {
                          Text(($r('app.string.management_running_tasks')))
                            .fontSize(18)
                            .fontWeight(FontWeight.Bold)
                            .fontColor(this.primaryTextColor())
                            .layoutWeight(1)
                            .maxLines(2)
                        }
                        .width('100%')
                        .alignItems(VerticalAlign.Center)
                        if ((this.ui.tasksStatus === ManagementSectionStatus.Content ||
                          this.ui.tasksStatus === ManagementSectionStatus.Error) && this.ui.tasks.length > 0) {
                          ForEach(this.ui.tasks, (task: ManagementTaskItem) => {
                            Column({ space: 7 }) {
                              Row({ space: 6 }) {
                                Text(task.name)
                                  .fontSize(14)
                                  .fontWeight(FontWeight.Medium)
                                  .fontColor(this.primaryTextColor())
                                  .layoutWeight(1)
                                  .maxLines(1)
                                  .textOverflow({ overflow: TextOverflow.Ellipsis })
                                Text(`${Math.round(task.progress)}%`)
                                  .fontSize(12)
                                  .fontColor($r('app.color.color_main'))
                                SymbolGlyph($r('sys.symbol.chevron_right'))
                                  .fontSize(12)
                                  .fontColor([this.secondaryTextColor()])
                              }
                              Progress({ value: task.progress, total: 100, type: ProgressType.Linear })
                                .height(5)
                                .color($r('app.color.color_main'))
                                .backgroundColor(this.progressTrackColor())
                            }
                            .width('100%')
                            .padding({ top: 8, bottom: 8 })
                            .borderRadius(10)
                            .stateStyles({ pressed: this.pressedRowStyle, normal: this.normalRowStyle })
                            .onClick(() => this.openTaskDetail(task))
                          }, (task: ManagementTaskItem) => task.id)
                          if (this.ui.tasksStatus === ManagementSectionStatus.Error) {
                            ManagementDashboardInlineState({
                              message: $r('app.string.management_stale_result'),
                              retry: true,
                              onRetry: () => this.vm.refreshTasks(),
                              nativeMaterial: this.useNativeMaterial(),
                              secondaryTextColor: this.secondaryTextColor()
                            })
                          }
                        } else if (this.ui.tasksStatus === ManagementSectionStatus.Empty) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_tasks_empty'),
                            retry: false,
                            onRetry: () => {},
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else if (this.ui.tasksStatus === ManagementSectionStatus.Loading) {
                          ManagementDashboardInlineLoading()
                        } else if (this.ui.tasksStatus === ManagementSectionStatus.Error) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_tasks_failed'),
                            retry: true,
                            onRetry: () => this.vm.refreshTasks(),
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        } else if (this.ui.tasksStatus === ManagementSectionStatus.Unsupported) {
                          ManagementDashboardInlineState({
                            message: $r('app.string.management_section_unsupported'),
                            retry: false,
                            onRetry: () => {},
                            nativeMaterial: this.useNativeMaterial(),
                            secondaryTextColor: this.secondaryTextColor()
                          })
                        }
                      }
                      .width('100%')
                    }
                    .width('100%')
                    .padding(16)
                    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                    .clip(true)
                    .backgroundColor(this.useNativeMaterial() ?
                      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                  }
                  .width(this.vm.appUIState.isBigScreen ? '57%' : '100%')
                  if (this.vm.appUIState.isBigScreen) {
                    Blank().width(12)
                  } else {
                    Blank().height(12)
                  }
                  Column({ space: 12 }) {
                    Column() {
                      ManagementSessionsDashboardSection({
                        ui: this.sessionsUI,
                        themeStyle: this.vm.appUIState.themeStyle,
                        nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                        onOpenDevices: () => this.openDevices(),
                        onOpenSession: (session: ManagementSessionItem) => this.openSessionDetail(session)
                      })
                    }
                    .width('100%')
                    .padding(16)
                    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                    .clip(true)
                    .backgroundColor(this.useNativeMaterial() ?
                      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                    Column() {
                      ManagementActivityDashboardSection({
                        ui: this.activityUI,
                        themeStyle: this.vm.appUIState.themeStyle,
                        nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
                        onRetry: () => this.activityVM.init(),
                        onOpenAll: () => this.openActivity()
                      })
                    }
                    .width('100%')
                    .padding(16)
                    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                    .clip(true)
                    .backgroundColor(this.useNativeMaterial() ?
                      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                  }
                  .width(this.vm.appUIState.isBigScreen ? '43%' : '100%')
                }
                .width('100%')
              }
              .width('100%')
              .constraintSize({ maxWidth: 1180 })
            }
            .width('100%')
            .justifyContent(FlexAlign.Center)
            .padding({ top: this.contentTopInset(this.showLegacyActionBar) })
          }
          .width('100%')
          .height('100%')
          .scrollBar(BarState.Off)
        }
        .layoutWeight(1)
        .pullDownRatio(0.3)
        .refreshOffset(64)
        .maxPullDownDistance(64 + this.vm.appUIState.safeTop)
        .onStateChange((status: RefreshStatus) => {
          this.ui.refreshStatus = status
        })
        .onOffsetChange((offset: number) => {
          this.ui.refreshOffset = offset
        })
        .onRefreshing(() => this.refreshAll())
        .padding({
          left: 16,
          right: 16,
          top: 12,
          bottom: this.vm.appUIState.safeBottom + 24
        })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementDashboardPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementDashboardPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_title'),
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
              ManagementDashboardContent({ showLegacyActionBar: false })
            } else {
              ManagementDashboardContent({ showLegacyActionBar: true })
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
        ManagementDashboardContent({ showLegacyActionBar: true })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
