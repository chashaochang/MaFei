import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { AppStorageV2 } from '@kit.ArkUI';
import { RouterConsts } from '../../common/RouterConsts';
import { UIConstants } from '../../common/UIConstants';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
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

@HMRouter({
  pageUrl: RouterConsts.ManagementTaskDetailPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementTaskDetailPage {
  @Local task: ManagementTaskItem | null = null
  @Local permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Local accessErrorCode: ManagementErrorCode = ManagementErrorCode.None
  @Local actionPending: boolean = false
  @Local actionMessage: string = ''
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private repository: ManagementRepository = new ManagementRepository()
  private pendingTask: ManagementTaskItem | null = null
  private accessGeneration: number = 0
  private readonly contentScroller: Scroller = new Scroller()

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

  @Builder
  detailRow(label: ResourceStr, value: ResourceStr, showDivider: boolean = true) {
    Column() {
      Row({ space: 16 }) {
        Text(label)
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
          .constraintSize({ minWidth: 92 })
        Text(value)
          .fontSize(13)
          .fontColor(this.primaryTextColor())
          .layoutWeight(1)
          .textAlign(TextAlign.End)
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
      }
      .width('100%')
      .constraintSize({ minHeight: 48 })
      .alignItems(VerticalAlign.Center)
      if (showDivider) {
        Divider()
          .height('0.5px')
          .backgroundColor(this.useNativeMaterial() ?
            $r('sys.color.comp_divider') : $r('app.color.border'))
      }
    }
    .width('100%')
  }

  @Builder
  taskStatusBody() {
    Column({ space: 12 }) {
      Row({ space: 12 }) {
        SymbolGlyph($r('sys.symbol.gearshape'))
          .fontSize(24)
          .fontColor([$r('app.color.color_main')])
          .width(48)
          .height(48)
          .backgroundColor(AppThemeSurfaceResolver.iconWellBackground(this.appUIState.themeStyle))
          .borderRadius(16)
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

  @Builder
  taskStatusGroup() {
    Column() {
      this.taskStatusBody()
    }
    .width('100%')
    .padding(18)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  taskStatusFeiniuGroup() {
    Column() {
      this.taskStatusBody()
    }
    .width('100%')
    .padding(18)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  taskStatusSurface() {
    if (this.useNativeMaterial()) {
      this.taskStatusGroup()
    } else {
      this.taskStatusFeiniuGroup()
    }
  }

  @Builder
  taskDetailsBody() {
    Column() {
      this.detailRow($r('app.string.management_detail_task_name'),
        this.displayValue(this.task?.name || ''))
      this.detailRow($r('app.string.management_detail_status'), this.stateText())
      this.detailRow($r('app.string.management_detail_category'),
        this.displayValue(this.task?.category || ''))
      this.detailRow($r('app.string.management_detail_progress'),
        `${Math.round(this.task?.progress || 0)}%`)
      this.detailRow($r('app.string.management_detail_task_key'),
        this.displayValue(this.task?.key || ''))
      this.detailRow($r('app.string.management_detail_last_start'),
        this.formatDate(this.task?.lastExecutionStart || ''))
      this.detailRow($r('app.string.management_detail_last_end'),
        this.formatDate(this.task?.lastExecutionEnd || ''))
      this.detailRow($r('app.string.management_detail_last_result'),
        this.displayValue(this.task?.lastExecutionStatus || ''), false)
    }
    .width('100%')
  }

  @Builder
  taskDetailsGroup() {
    Column() {
      this.taskDetailsBody()
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  taskDetailsFeiniuGroup() {
    Column() {
      this.taskDetailsBody()
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  taskDetailsSurface() {
    if (this.useNativeMaterial()) {
      this.taskDetailsGroup()
    } else {
      this.taskDetailsFeiniuGroup()
    }
  }

  @Builder
  taskDescriptionBody() {
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

  @Builder
  taskDescriptionGroup() {
    Column() {
      this.taskDescriptionBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  taskDescriptionFeiniuGroup() {
    Column() {
      this.taskDescriptionBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  taskDescriptionSurface() {
    if (this.useNativeMaterial()) {
      this.taskDescriptionGroup()
    } else {
      this.taskDescriptionFeiniuGroup()
    }
  }

  @Builder
  taskErrorBody() {
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

  @Builder
  taskErrorGroup() {
    Column() {
      this.taskErrorBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  taskErrorFeiniuGroup() {
    Column() {
      this.taskErrorBody()
    }
    .width('100%')
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  taskErrorSurface() {
    if (this.useNativeMaterial()) {
      this.taskErrorGroup()
    } else {
      this.taskErrorFeiniuGroup()
    }
  }

  @Builder
  content() {
    Column({ space: 12 }) {
      this.taskStatusSurface()
      this.taskDetailsSurface()
      if (this.task?.description) {
        this.taskDescriptionSurface()
      }
      if (this.task?.lastExecutionError) {
        this.taskErrorSurface()
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

  private showTaskAction(): boolean {
    if (!this.task) return false
    return ManagementTaskActionPolicy.canStart(this.task) || ManagementTaskActionPolicy.canCancel(this.task)
  }

  @Builder
  private taskActionButton() {
    Button(this.taskActionLabel(), { type: ButtonType.Normal })
      .width('100%')
      .height(46)
      .fontColor(this.task?.busy ? $r('app.color.main_red_color') : $r('app.color.on_primary'))
      .backgroundColor(this.useNativeMaterial() ? Color.Transparent :
        (this.task?.busy ? Color.Transparent : $r('app.color.color_main')))
      .borderRadius(10)
      .systemMaterial(this.useNativeMaterial() && !this.task?.busy ?
        AppThemeSurfaceResolver.material(AppThemeMaterialRole.PrimaryInteractive) :
        AppThemeSurfaceResolver.disabledSystemMaterial())
      .enabled(!this.actionPending)
      .onClick(() => this.runTaskAction())
  }

  @Builder
  private taskActionBar() {
    Column() {
      this.taskActionButton()
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 10, bottom: this.appUIState.safeBottom + 10 })
    .systemMaterial(this.useNativeMaterial() ?
      AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating) :
      AppThemeSurfaceResolver.disabledSystemMaterial())
    .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.bg_1'))
  }

  @Builder
  accessStateView() {
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
            .backgroundColor($r('app.color.color_main'))
            .borderRadius(12)
            .onClick(() => this.checkAdministratorAccess())
        }
      }
    }
    .layoutWeight(1)
    .width('100%')
    .padding(24)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({ title: $r('app.string.management_task_detail_title') })
      }
      if (this.task) {
        Stack({ alignContent: Alignment.Bottom }) {
          Scroll(this.contentScroller) {
            Column({ space: 12 }) {
              if (!showLegacyActionBar) {
                Blank().height(this.nativeTopSpacer())
              }
              this.content()
            }
            .width('100%')
            .constraintSize({ maxWidth: 720 })
            .padding({
              left: this.appUIState.deviceWidth >= 600 ? 24 : 16,
              right: this.appUIState.deviceWidth >= 600 ? 24 : 16,
              top: showLegacyActionBar ? 12 : 0,
              bottom: ManagementNativePagePolicy.contentBottomPadding(
                this.appUIState.safeBottom + 24, this.showTaskAction())
            })
          }
          .width('100%')
          .height('100%')
          .scrollBar(BarState.Auto)
          if (this.showTaskAction()) {
            this.taskActionBar()
          }
        }
        .layoutWeight(1)
        .width('100%')
      } else {
        this.accessStateView()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_task_detail_title'),
      contentExtendsUnderTitleBar: true,
      scrollControllers: [this.contentScroller],
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
