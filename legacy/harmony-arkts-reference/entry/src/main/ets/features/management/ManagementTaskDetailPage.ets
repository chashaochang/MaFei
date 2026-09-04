import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { AppStorageV2 } from '@kit.ArkUI';
import { RouterConsts } from '../../common/RouterConsts';
import { UIConstants } from '../../common/UIConstants';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { AppUIState } from '../../entity/AppUIState';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementTaskActionCode,
  ManagementTaskActionResult,
  ManagementTaskItem
} from './ManagementModels';
import { ManagementNativePagePolicy } from './ManagementNativePagePolicy';
import { ManagementRepository } from './ManagementRepository';
import { ManagementTaskActionPolicy } from './ManagementTaskActionPolicy';
import { ManagementDetailRow } from './ManagementDetailRow';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

@ComponentV2
struct ManagementTaskDetailContent {
  @Param showLegacyActionBar: boolean = false
  @Param contentScroller: Scroller = new Scroller()
  @Local task: ManagementTaskItem | null = null
  @Local permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Local accessErrorCode: ManagementErrorCode = ManagementErrorCode.None
  @Local actionPending: boolean = false
  @Local actionMessage: string = ''
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private repository: ManagementRepository = new ManagementRepository()
  private pendingTask: ManagementTaskItem | null = null
  private accessGeneration: number = 0

  aboutToAppear(): void {
    this.accessGeneration += 1
    this.task = null
    this.pendingTask = null
    this.permissionState = ManagementPermissionState.Checking
    this.accessErrorCode = ManagementErrorCode.None
    this.actionPending = false
    this.actionMessage = ''
    const param = HMRouterMgr.getCurrentParam() as object
    if (!param) {
      this.permissionState = ManagementPermissionState.Error
      return
    }
    const task = param['task'] as ManagementTaskItem
    if (!task || typeof task.id !== 'string' || task.id.length === 0 || typeof task.name !== 'string' ||
      typeof task.running !== 'boolean' || typeof task.progress !== 'number') {
      this.permissionState = ManagementPermissionState.Error
      return
    }
    this.pendingTask = task
    this.checkAdministratorAccess()
  }

  aboutToDisappear(): void {
    this.accessGeneration += 1
  }

  private async checkAdministratorAccess(): Promise<void> {
    const candidate = this.pendingTask
    if (!candidate || !this.repository.isApiClientInitialized()) {
      this.permissionState = ManagementPermissionState.Error
      this.accessErrorCode = ManagementErrorCode.RequestFailed
      return
    }
    const generation = this.accessGeneration
    this.permissionState = ManagementPermissionState.Checking
    this.accessErrorCode = ManagementErrorCode.None
    const accessCode = await this.repository.checkCurrentUserAccess()
    if (generation !== this.accessGeneration) {
      return
    }
    this.accessErrorCode = accessCode
    if (accessCode === ManagementErrorCode.None) {
      this.task = candidate
      this.permissionState = ManagementPermissionState.Allowed
    } else if (accessCode === ManagementErrorCode.PermissionDenied) {
      this.permissionState = ManagementPermissionState.Denied
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_permission_denied')
      })
      HMRouterMgr.pop()
    } else {
      this.permissionState = ManagementPermissionState.Error
    }
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
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

  private nativeTopSpacer(): number {
    return Math.max(0, this.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private taskActionLabel(): ResourceStr {
    if (this.actionPending) {
      return this.task?.busy ? $r('app.string.management_task_cancelling') :
        $r('app.string.management_task_starting')
    }
    return this.task?.busy ? $r('app.string.management_task_cancel') :
      $r('app.string.management_task_start')
  }

  private taskActionMessage(result: ManagementTaskActionResult): ResourceStr {
    if (result.code === ManagementTaskActionCode.Started) {
      return $r('app.string.management_task_started')
    }
    if (result.code === ManagementTaskActionCode.Cancelled) {
      return $r('app.string.management_task_cancelled')
    }
    if (result.code === ManagementTaskActionCode.PermissionDenied) {
      return $r('app.string.management_permission_denied')
    }
    return $r('app.string.management_task_action_failed')
  }

  private async refreshTask(): Promise<void> {
    const taskId = this.task?.id || this.pendingTask?.id || ''
    if (!taskId) return
    try {
      const refreshed = await this.repository.loadTask(taskId)
      if (refreshed) {
        this.task = refreshed
        this.pendingTask = refreshed
      }
    } catch (_error) {
    }
  }

  private async runTaskAction(): Promise<void> {
    const task = this.task
    if (!task || this.actionPending) return
    this.actionPending = true
    const result = task.busy ? await this.repository.cancelTask(task) :
      await this.repository.startTask(task)
    this.actionMessage = result.message
    this.getUIContext().getPromptAction().showToast({ message: this.taskActionMessage(result) })
    await this.refreshTask()
    this.actionPending = false
  }

  private displayValue(value: string): ResourceStr {
    return value ? value : $r('app.string.management_detail_unknown')
  }

  private stateText(): ResourceStr {
    if (this.task?.running) {
      return $r('app.string.management_detail_running')
    }
    return this.task?.state === 'Cancelling' ? $r('app.string.management_detail_cancelling') :
      this.displayValue(this.task?.state || '')
  }

  private formatDate(value: string): ResourceStr {
    if (!value) {
      return $r('app.string.management_detail_unknown')
    }
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) {
      return value
    }
    const year = date.getFullYear()
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${year}-${month}-${day} ${hour}:${minute}`
  }



















  private showTaskAction(): boolean {
    if (!this.task) return false
    return ManagementTaskActionPolicy.canStart(this.task) || ManagementTaskActionPolicy.canCancel(this.task)
  }




  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: $r('app.string.management_task_detail_title') })
      }
      if (this.task) {
        Stack({ alignContent: Alignment.Bottom }) {
          Scroll(this.contentScroller) {
            Column({ space: 12 }) {
              if (!this.showLegacyActionBar) {
                Blank().height(this.nativeTopSpacer())
              }
              Column({ space: 12 }) {
                Column() {
                  Column({ space: 12 }) {
                    Row({ space: 12 }) {
                      SymbolGlyph($r('sys.symbol.gearshape'))
                        .fontSize(24)
                        .fontColor([$r('app.color.color_main')])
                        .width(48)
                        .height(48)
                        .backgroundColor(AppThemeSurfaceResolver.iconWellBackground(this.appUIState.themeStyle))
                        .borderRadius(8)
                      Column({ space: 4 }) {
                        Text(this.displayValue(this.task?.name || ''))
                          .fontSize(18)
                          .fontWeight(FontWeight.Bold)
                          .fontColor(this.primaryTextColor())
                          .maxLines(2)
                          .textOverflow({ overflow: TextOverflow.Ellipsis })
                        Text(this.stateText())
                          .fontSize(12)
                          .fontColor(this.task?.running ? $r('app.color.status_success') : this.secondaryTextColor())
                      }
                      .layoutWeight(1)
                      .alignItems(HorizontalAlign.Start)
                      Text(`${Math.round(this.task?.progress || 0)}%`)
                        .fontSize(15)
                        .fontWeight(FontWeight.Bold)
                        .fontColor($r('app.color.color_main'))
                    }
                    .width('100%')
                    Progress({ value: this.task?.progress || 0, total: 100, type: ProgressType.Linear })
                      .height(6)
                      .color($r('app.color.color_main'))
                      .backgroundColor(this.progressTrackColor())
                  }
                  .width('100%')
                }
                .width('100%')
                .padding(18)
                .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
                .clip(this.useNativeMaterial())
                .backgroundColor(this.useNativeMaterial() ?
                  AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                Column() {
                  Column() {
                    ManagementDetailRow({
                      label: $r('app.string.management_detail_task_name'),
                      value: this.displayValue(this.task?.name || ''),
                      showDivider: true,
                      primaryTextColor: this.primaryTextColor(),
                      secondaryTextColor: this.secondaryTextColor(),
                      dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                    })
                    ManagementDetailRow({
                      label: $r('app.string.management_detail_status'),
                      value: this.stateText(),
                      showDivider: true,
                      primaryTextColor: this.primaryTextColor(),
                      secondaryTextColor: this.secondaryTextColor(),
                      dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                    })
                    ManagementDetailRow({
                      label: $r('app.string.management_detail_category'),
                      value: this.displayValue(this.task?.category || ''),
                      showDivider: true,
                      primaryTextColor: this.primaryTextColor(),
                      secondaryTextColor: this.secondaryTextColor(),
                      dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                    })
                    ManagementDetailRow({
                      label: $r('app.string.management_detail_progress'),
                      value: `${Math.round(this.task?.progress || 0)}%`,
                      showDivider: true,
                      primaryTextColor: this.primaryTextColor(),
                      secondaryTextColor: this.secondaryTextColor(),
                      dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                    })
                    ManagementDetailRow({
                      label: $r('app.string.management_detail_task_key'),
                      value: this.displayValue(this.task?.key || ''),
                      showDivider: true,
                      primaryTextColor: this.primaryTextColor(),
                      secondaryTextColor: this.secondaryTextColor(),
                      dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                    })
                    ManagementDetailRow({
                      label: $r('app.string.management_detail_last_start'),
                      value: this.formatDate(this.task?.lastExecutionStart || ''),
                      showDivider: true,
                      primaryTextColor: this.primaryTextColor(),
                      secondaryTextColor: this.secondaryTextColor(),
                      dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                    })
                    ManagementDetailRow({
                      label: $r('app.string.management_detail_last_end'),
                      value: this.formatDate(this.task?.lastExecutionEnd || ''),
                      showDivider: true,
                      primaryTextColor: this.primaryTextColor(),
                      secondaryTextColor: this.secondaryTextColor(),
                      dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                    })
                    ManagementDetailRow({
                      label: $r('app.string.management_detail_last_result'),
                      value: this.displayValue(this.task?.lastExecutionStatus || ''),
                      showDivider: false,
                      primaryTextColor: this.primaryTextColor(),
                      secondaryTextColor: this.secondaryTextColor(),
                      dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                    })
                  }
                  .width('100%')
                }
                .width('100%')
                .padding({ left: 16, right: 16 })
                .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
                .clip(this.useNativeMaterial())
                .backgroundColor(this.useNativeMaterial() ?
                  AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                if (this.task?.description) {
                  Column() {
                    Column({ space: 8 }) {
                      Text($r('app.string.management_detail_description'))
                        .fontSize(13)
                        .fontWeight(FontWeight.Medium)
                        .fontColor(this.secondaryTextColor())
                      Text(this.task?.description || '')
                        .fontSize(13)
                        .fontColor(this.primaryTextColor())
                    }
                    .width('100%')
                    .alignItems(HorizontalAlign.Start)
                  }
                  .width('100%')
                  .padding(16)
                  .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
                  .clip(this.useNativeMaterial())
                  .backgroundColor(this.useNativeMaterial() ?
                    AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                }
                if (this.task?.lastExecutionError) {
                  Column() {
                    Column({ space: 8 }) {
                      Text($r('app.string.management_detail_last_error'))
                        .fontSize(13)
                        .fontWeight(FontWeight.Medium)
                        .fontColor($r('app.color.main_red_color'))
                      Text(this.task?.lastExecutionError || '')
                        .fontSize(13)
                        .fontColor(this.primaryTextColor())
                    }
                    .width('100%')
                    .alignItems(HorizontalAlign.Start)
                  }
                  .width('100%')
                  .padding(16)
                  .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
                  .clip(this.useNativeMaterial())
                  .backgroundColor(this.useNativeMaterial() ?
                    AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
                }
                if (this.actionMessage) {
                  Text(this.actionMessage)
                    .fontSize(12)
                    .fontColor($r('app.color.main_red_color'))
                    .width('100%')
                    .padding({ left: 4, right: 4 })
                }
              }
              .width('100%')
            }
            .width('100%')
            .constraintSize({ maxWidth: 720 })
            .padding({
              left: this.appUIState.deviceWidth >= 600 ? 24 : 16,
              right: this.appUIState.deviceWidth >= 600 ? 24 : 16,
              top: this.showLegacyActionBar ? 12 : 0,
              bottom: ManagementNativePagePolicy.contentBottomPadding(
                this.appUIState.safeBottom + 24, this.showTaskAction())
            })
          }
          .width('100%')
          .height('100%')
          .scrollBar(BarState.Auto)
          if (this.showTaskAction()) {
            Column() {
              Button(this.taskActionLabel(), { type: ButtonType.Normal })
                .width('100%')
                .height(46)
                .fontColor(this.task?.busy ? $r('app.color.main_red_color') : $r('app.color.on_primary'))
                .backgroundColor(this.useNativeMaterial() ? Color.Transparent :
                  (this.task?.busy ? Color.Transparent : $r('app.color.color_main')))
                .borderRadius(this.useNativeMaterial() ? 23 : 10)
                .attributeModifier(AppThemeSurfaceResolver.modifier(
                  this.task?.busy ? AppThemeMaterialRole.AdaptiveControl :
                    AppThemeMaterialRole.PrimaryInteractive,
                  this.useNativeMaterial()))
                .enabled(!this.actionPending)
                .onClick(() => this.runTaskAction())
            }
            .width('100%')
            .padding({ left: 16, right: 16, top: 10, bottom: this.appUIState.safeBottom + 10 })
            .attributeModifier(AppThemeSurfaceResolver.modifier(
              AppThemeMaterialRole.Floating, this.useNativeMaterial()))
            .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'))
          }
        }
        .layoutWeight(1)
        .width('100%')
      } else {
        Column({ space: 14 }) {
          if (this.permissionState === ManagementPermissionState.Checking) {
            LoadingProgress()
              .width(28)
              .height(28)
              .color($r('app.color.color_main'))
          } else {
            Text(this.pendingTask ?
              (this.accessErrorCode === ManagementErrorCode.Offline ? $r('app.string.management_access_offline') :
                $r('app.string.management_permission_check_failed')) :
              $r('app.string.management_detail_unavailable'))
              .fontSize(14)
              .fontColor(this.secondaryTextColor())
              .textAlign(TextAlign.Center)
              .maxLines(3)
            if (this.pendingTask && this.accessErrorCode !== ManagementErrorCode.AuthenticationExpired) {
              Button($r('app.string.management_retry'), { type: ButtonType.Normal })
                .height(38)
                .padding({ left: 22, right: 22 })
                .fontSize(14)
                .fontColor($r('app.color.on_primary'))
                .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
                .borderRadius(this.useNativeMaterial() ? 19 : 12)
                .attributeModifier(AppThemeSurfaceResolver.modifier(
                  AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
                .onClick(() => this.checkAdministratorAccess())
            }
          }
        }
        .layoutWeight(1)
        .width('100%')
        .padding(24)
        .justifyContent(FlexAlign.Center)
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementTaskDetailPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementTaskDetailPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly contentScroller: Scroller = new Scroller()

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_task_detail_title'),
        contentExtendsUnderTitleBar: true,
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
              ManagementTaskDetailContent({
                showLegacyActionBar: false,
                contentScroller: this.contentScroller
              })
            } else {
              ManagementTaskDetailContent({
                showLegacyActionBar: true,
                contentScroller: this.contentScroller
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
        ManagementTaskDetailContent({
          showLegacyActionBar: true,
          contentScroller: this.contentScroller
        })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
