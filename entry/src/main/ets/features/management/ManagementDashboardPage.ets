import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter';
import { deviceInfo } from '@kit.BasicServicesKit';
import { PageState } from '../../common/base/BaseUIState';
import { CustomRefreshComponent } from '../../common/CustomRefreshComponent';
import { RouterConsts } from '../../common/RouterConsts';
import { UIConstants } from '../../common/UIConstants';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
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

@HMRouter({
  pageUrl: RouterConsts.ManagementDashboardPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementDashboardPage {
  @Local ui: ManagementDashboardUIState = new ManagementDashboardUIState(this.getUIContext())
  @Local vm: ManagementDashboardViewModel = new ManagementDashboardViewModel(this.ui)
  @Local sessionsUI: ManagementSessionsUIState = new ManagementSessionsUIState(this.getUIContext())
  @Local sessionsVM: ManagementSessionsViewModel = new ManagementSessionsViewModel(this.sessionsUI)
  @Local activityUI: ManagementActivityDashboardUIState = new ManagementActivityDashboardUIState(this.getUIContext())
  @Local activityVM: ManagementActivityDashboardViewModel = new ManagementActivityDashboardViewModel(this.activityUI)
  private permissionPopTimer: number = -1
  private lifecycleOwner: IHMLifecycleOwner | null = null

  private contentTopInset(showLegacyActionBar: boolean): number {
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

  private async refreshAll(): Promise<void> {
    await Promise.all([
      this.vm.refresh(),
      this.sessionsVM.refresh(),
      this.activityVM.refresh()
    ])
  }

  @Builder
  sectionHeader(title: ResourceStr) {
    Row() {
      Text(title)
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
        .layoutWeight(1)
        .maxLines(2)
    }
    .width('100%')
    .alignItems(VerticalAlign.Center)
  }

  @Builder
  metric(label: ResourceStr, value: number) {
    Column({ space: 4 }) {
      Text(value.toString())
        .fontSize(22)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
      Text(label)
        .fontSize(12)
        .fontColor(this.secondaryTextColor())
    }
    .layoutWeight(1)
    .alignItems(HorizontalAlign.Start)
  }

  @Builder
  overviewCardBody() {
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
          this.inlineState($r('app.string.management_stale_result'), true, () => this.vm.refreshServer())
        }
      } else if (this.ui.serverStatus === ManagementSectionStatus.Content) {
        this.inlineState($r('app.string.management_server_failed'), true, () => this.vm.refreshServer())
      } else if (this.ui.serverStatus === ManagementSectionStatus.Loading) {
        this.inlineLoadingState()
      } else if (this.ui.serverStatus === ManagementSectionStatus.Empty) {
        this.inlineState($r('app.string.management_access_no_server'))
      } else if (this.ui.serverStatus === ManagementSectionStatus.Unsupported) {
        this.inlineState($r('app.string.management_section_unsupported'))
      } else if (this.ui.serverStatus === ManagementSectionStatus.Error) {
        this.inlineState($r('app.string.management_server_failed'), true, () => this.vm.refreshServer())
      }

      if ((this.ui.countsStatus === ManagementSectionStatus.Content ||
        this.ui.countsStatus === ManagementSectionStatus.Error) && this.ui.counts) {
        Row() {
          this.metric($r('app.string.management_movies'), this.ui.counts.movies)
          this.metric($r('app.string.management_series'), this.ui.counts.series)
          this.metric($r('app.string.management_episodes'), this.ui.counts.episodes)
        }
        .width('100%')
        if (this.ui.countsStatus === ManagementSectionStatus.Error) {
          this.inlineState($r('app.string.management_stale_result'), true, () => this.vm.refreshCounts())
        }
      } else if (this.ui.countsStatus === ManagementSectionStatus.Content) {
        this.inlineState($r('app.string.management_counts_failed'), true, () => this.vm.refreshCounts())
      } else if (this.ui.countsStatus === ManagementSectionStatus.Loading) {
        this.inlineLoadingState()
      } else if (this.ui.countsStatus === ManagementSectionStatus.Empty) {
        Row() {
          this.metric($r('app.string.management_movies'), 0)
          this.metric($r('app.string.management_series'), 0)
          this.metric($r('app.string.management_episodes'), 0)
        }
        .width('100%')
      } else if (this.ui.countsStatus === ManagementSectionStatus.Error) {
        this.inlineState($r('app.string.management_counts_failed'), true, () => this.vm.refreshCounts())
      } else if (this.ui.countsStatus === ManagementSectionStatus.Unsupported) {
        this.inlineState($r('app.string.management_section_unsupported'))
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
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .enabled(!this.ui.isLibraryScanRunning && !this.ui.isStartingScan && !this.ui.isScanSubmissionLocked)
        .opacity(this.ui.isLibraryScanRunning || this.ui.isStartingScan ||
          this.ui.isScanSubmissionLocked ? 0.55 : 1)
        .onClick(() => {
          this.confirmLibraryScan()
        })
    }
    .width('100%')
  }

  @Builder
  overviewCard() {
    Column() {
      this.overviewCardBody()
    }
    .width('100%')
    .padding(18)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  feiniuOverviewCard() {
    Column() {
      this.overviewCardBody()
    }
    .width('100%')
    .padding(18)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  overviewCardSurface() {
    if (this.useNativeMaterial()) {
      this.overviewCard()
    } else {
      this.feiniuOverviewCard()
    }
  }

  @Builder
  usersCardBody() {
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
          this.metric($r('app.string.management_user_summary_total'), this.ui.users.total)
          this.metric($r('app.string.management_user_summary_administrators'), this.ui.users.administrators)
          this.metric($r('app.string.management_user_summary_disabled'), this.ui.users.disabled)
        }
        .width('100%')
        if (this.ui.usersStatus === ManagementSectionStatus.Error) {
          this.inlineState($r('app.string.management_stale_result'), true, () => this.vm.refreshUsers())
        }
      } else if (this.ui.usersStatus === ManagementSectionStatus.Loading) {
        this.inlineLoadingState()
      } else if (this.ui.usersStatus === ManagementSectionStatus.Error) {
        this.inlineState($r('app.string.management_user_load_failed'), true, () => this.vm.refreshUsers())
      } else if (this.ui.usersStatus === ManagementSectionStatus.Unsupported) {
        this.inlineState($r('app.string.management_section_unsupported'))
      } else {
        Row() {
          this.metric($r('app.string.management_user_summary_total'), 0)
          this.metric($r('app.string.management_user_summary_administrators'), 0)
          this.metric($r('app.string.management_user_summary_disabled'), 0)
        }
        .width('100%')
      }
    }
    .width('100%')
  }

  @Builder
  usersCard() {
    Column() {
      this.usersCardBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  feiniuUsersCard() {
    Column() {
      this.usersCardBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  usersCardSurface() {
    if (this.useNativeMaterial()) {
      this.usersCard()
    } else {
      this.feiniuUsersCard()
    }
  }

  @Builder
  inlineState(message: ResourceStr, retry: boolean = false, onRetry?: () => void) {
    Row({ space: 8 }) {
      SymbolGlyph($r('sys.symbol.exclamationmark_circle'))
        .fontSize(18)
        .fontColor([this.secondaryTextColor()])
      Text(message)
        .fontSize(13)
        .fontColor(this.secondaryTextColor())
        .layoutWeight(1)
      if (retry && onRetry) {
        Button($r('app.string.management_retry'), { type: ButtonType.Normal })
          .height(30)
          .padding({ left: 12, right: 12 })
          .borderRadius(this.useNativeMaterial() ? 15 : 8)
          .fontSize(12)
          .fontColor($r('app.color.color_main'))
          .backgroundColor(Color.Transparent)
          .systemMaterial(this.useNativeMaterial() ?
            AppThemeSurfaceResolver.material(AppThemeMaterialRole.AdaptiveInteractive) :
            AppThemeSurfaceResolver.disabledSystemMaterial())
          .onClick(onRetry)
      }
    }
    .width('100%')
    .padding({ top: 10, bottom: 10 })
  }

  @Builder
  inlineLoadingState() {
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

  @Builder
  tasksCardBody() {
    Column({ space: 10 }) {
      this.sectionHeader($r('app.string.management_running_tasks'))
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
          this.inlineState($r('app.string.management_stale_result'), true, () => this.vm.refreshTasks())
        }
      } else if (this.ui.tasksStatus === ManagementSectionStatus.Empty) {
        this.inlineState($r('app.string.management_tasks_empty'))
      } else if (this.ui.tasksStatus === ManagementSectionStatus.Loading) {
        this.inlineLoadingState()
      } else if (this.ui.tasksStatus === ManagementSectionStatus.Error) {
        this.inlineState($r('app.string.management_tasks_failed'), true, () => this.vm.refreshTasks())
      } else if (this.ui.tasksStatus === ManagementSectionStatus.Unsupported) {
        this.inlineState($r('app.string.management_section_unsupported'))
      }
    }
    .width('100%')
  }

  @Builder
  tasksCard() {
    Column() {
      this.tasksCardBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  feiniuTasksCard() {
    Column() {
      this.tasksCardBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  tasksCardSurface() {
    if (this.useNativeMaterial()) {
      this.tasksCard()
    } else {
      this.feiniuTasksCard()
    }
  }

  @Builder
  sessionsCardBody() {
    ManagementSessionsDashboardSection({
      ui: this.sessionsUI,
      themeStyle: this.vm.appUIState.themeStyle,
      nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
      onOpenDevices: () => this.openDevices(),
      onOpenSession: (session: ManagementSessionItem) => this.openSessionDetail(session)
    })
  }

  @Builder
  sessionsCard() {
    Column() {
      this.sessionsCardBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  feiniuSessionsCard() {
    Column() {
      this.sessionsCardBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  sessionsCardSurface() {
    if (this.useNativeMaterial()) {
      this.sessionsCard()
    } else {
      this.feiniuSessionsCard()
    }
  }

  @Builder
  activityCardBody() {
    ManagementActivityDashboardSection({
      ui: this.activityUI,
      themeStyle: this.vm.appUIState.themeStyle,
      nativeThemeAvailable: this.vm.appUIState.nativeThemeAvailable,
      onRetry: () => this.activityVM.init(),
      onOpenAll: () => this.openActivity()
    })
  }

  @Builder
  activityCard() {
    Column() {
      this.activityCardBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  feiniuActivityCard() {
    Column() {
      this.activityCardBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  activityCardSurface() {
    if (this.useNativeMaterial()) {
      this.activityCard()
    } else {
      this.feiniuActivityCard()
    }
  }

  @Builder
  dashboardContent() {
    if (this.vm.appUIState.isBigScreen) {
      Row({ space: 12 }) {
        Column({ space: 12 }) {
          this.overviewCardSurface()
          this.usersCardSurface()
          this.tasksCardSurface()
        }
        .layoutWeight(1.15)
        Column({ space: 12 }) {
          this.sessionsCardSurface()
          this.activityCardSurface()
        }
        .layoutWeight(0.85)
      }
      .width('100%')
      .alignItems(VerticalAlign.Top)
    } else {
      Column({ space: 12 }) {
        this.overviewCardSurface()
        this.usersCardSurface()
        this.tasksCardSurface()
        this.sessionsCardSurface()
        this.activityCardSurface()
      }
      .width('100%')
    }
  }

  @Builder
  accessErrorView(topInset: number = 0) {
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
        .systemMaterial(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
          AppThemeSurfaceResolver.disabledSystemMaterial())
        .onClick(() => this.vm.init())
    }
    .layoutWeight(1)
    .width('100%')
    .padding({ left: 24, right: 24, top: 24 + topInset, bottom: 24 })
    .justifyContent(FlexAlign.Center)
  }

  private confirmLibraryScan(): void {
    if (deviceInfo.sdkApiVersion >= 26) {
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

  @Builder
  refreshBuilder() {
    CustomRefreshComponent({
      refreshOffset: this.ui.refreshOffset,
      refreshStatus: this.ui.refreshStatus
    })
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({ title: $r('app.string.management_title') })
      }
      if (this.ui.pageState === PageState.Loading) {
        ManagementDashboardSkeleton({
          feiniuCardColor: $r('app.color.bg_1'),
          cardRadius: AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle),
          isBigScreen: this.vm.appUIState.isBigScreen,
          useNativeMaterial: this.useNativeMaterial()
        })
          .padding({ top: this.contentTopInset(showLegacyActionBar) })
          .layoutWeight(1)
      } else if (this.ui.pageState === PageState.Error) {
        this.accessErrorView(this.contentTopInset(showLegacyActionBar))
      } else {
        Refresh({ refreshing: $$this.ui.isRefreshing, builder: this.refreshBuilder() }) {
          Scroll() {
            Row() {
              Column() {
                this.dashboardContent()
              }
              .width('100%')
              .constraintSize({ maxWidth: 1180 })
            }
            .width('100%')
            .justifyContent(FlexAlign.Center)
            .padding({ top: this.contentTopInset(showLegacyActionBar) })
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
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_title'),
      contentExtendsUnderTitleBar: true,
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
