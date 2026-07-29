import { HMRouter, HMRouterMgr } from '@hadss/hmrouter';
import { AppStorageV2 } from '@kit.ArkUI';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination } from '../../component/AppRouteDestination';
import { AppUIState } from '../../entity/AppUIState';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../theme/AppThemeTokens';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSessionItem
} from './ManagementModels';
import { ManagementRepository } from './ManagementRepository';

@HMRouter({
  pageUrl: RouterConsts.ManagementSessionDetailPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementSessionDetailPage {
  @Local session: ManagementSessionItem | null = null
  @Local permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Local accessErrorCode: ManagementErrorCode = ManagementErrorCode.None
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!
  private repository: ManagementRepository = new ManagementRepository()
  private pendingSession: ManagementSessionItem | null = null
  private accessGeneration: number = 0

  aboutToAppear(): void {
    this.accessGeneration += 1
    this.session = null
    this.pendingSession = null
    this.permissionState = ManagementPermissionState.Checking
    this.accessErrorCode = ManagementErrorCode.None
    const param = HMRouterMgr.getCurrentParam() as object
    if (!param) {
      this.permissionState = ManagementPermissionState.Error
      return
    }
    const session = param['session'] as ManagementSessionItem
    if (!session || typeof session.id !== 'string' || session.id.length === 0 ||
      typeof session.itemName !== 'string' || typeof session.paused !== 'boolean') {
      this.permissionState = ManagementPermissionState.Error
      return
    }
    this.pendingSession = session
    this.checkAdministratorAccess()
  }

  aboutToDisappear(): void {
    this.accessGeneration += 1
  }

  private async checkAdministratorAccess(): Promise<void> {
    const candidate = this.pendingSession
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
      this.session = candidate
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

  private primaryTextColor(): ResourceColor {
    return this.useNativeMaterial() ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.useNativeMaterial() ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private displayValue(value: string): ResourceStr {
    return value ? value : $r('app.string.management_detail_unknown')
  }

  private supportText(supported: boolean): ResourceStr {
    return supported ? $r('app.string.management_detail_supported') :
      $r('app.string.management_detail_unsupported')
  }

  private statusText(): ResourceStr {
    return this.session?.paused ? $r('app.string.management_detail_paused') :
      $r('app.string.management_detail_playing')
  }

  private playMethodText(value: string): ResourceStr {
    if (value === 'DirectPlay') {
      return $r('app.string.management_detail_direct_play')
    }
    if (value === 'DirectStream') {
      return $r('app.string.management_detail_direct_stream')
    }
    if (value === 'Transcode') {
      return $r('app.string.management_detail_transcode')
    }
    return this.displayValue(value)
  }

  private formatTicks(ticks: number): string {
    const totalSeconds = Math.max(0, Math.floor(ticks / 10000000))
    const hours = Math.floor(totalSeconds / 3600)
    const minutes = Math.floor((totalSeconds % 3600) / 60)
    const seconds = totalSeconds % 60
    const minuteText = minutes.toString().padStart(2, '0')
    const secondText = seconds.toString().padStart(2, '0')
    return hours > 0 ? `${hours}:${minuteText}:${secondText}` : `${minutes}:${secondText}`
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

  private volumeText(): ResourceStr {
    if (!this.session || this.session.volumeLevel < 0) {
      return $r('app.string.management_detail_unknown')
    }
    return this.session.muted ? $r('app.string.management_detail_muted') : `${this.session.volumeLevel}%`
  }

  private streamIndexText(index: number): ResourceStr {
    return index >= 0 ? index.toString() : $r('app.string.management_detail_unknown')
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
  sessionSummaryBody() {
    Row({ space: 12 }) {
      SymbolGlyph(this.session?.paused ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill'))
        .fontSize(24)
        .fontColor([$r('app.color.color_main')])
        .width(48)
        .height(48)
        .backgroundColor(AppThemeSurfaceResolver.iconWellBackground(this.appUIState.themeStyle))
        .borderRadius(16)
      Column({ space: 4 }) {
        Text(this.displayValue(this.session?.itemName || ''))
          .fontSize(18)
          .fontWeight(FontWeight.Bold)
          .fontColor(this.primaryTextColor())
          .maxLines(2)
          .textOverflow({ overflow: TextOverflow.Ellipsis })
        Text(this.statusText())
          .fontSize(12)
          .fontColor(this.session?.paused ? this.secondaryTextColor() : $r('app.color.status_success'))
      }
      .layoutWeight(1)
      .alignItems(HorizontalAlign.Start)
    }
    .width('100%')
  }

  @Builder
  sessionSummaryGroup() {
    Column() {
      this.sessionSummaryBody()
    }
    .width('100%')
    .padding(18)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  sessionSummaryFeiniuGroup() {
    Column() {
      this.sessionSummaryBody()
    }
    .width('100%')
    .padding(18)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  sessionSummarySurface() {
    if (this.useNativeMaterial()) {
      this.sessionSummaryGroup()
    } else {
      this.sessionSummaryFeiniuGroup()
    }
  }

  @Builder
  sessionDetailsBody() {
    Column() {
      this.detailRow($r('app.string.management_detail_user'),
        this.displayValue(this.session?.userName || ''))
      this.detailRow($r('app.string.management_detail_position'),
        this.formatTicks(this.session?.positionTicks || 0))
      this.detailRow($r('app.string.management_detail_device'),
        this.displayValue(this.session?.deviceName || ''))
      this.detailRow($r('app.string.management_detail_device_type'),
        this.displayValue(this.session?.deviceType || ''))
      this.detailRow($r('app.string.management_detail_client'),
        this.displayValue(this.session?.client || ''))
      this.detailRow($r('app.string.management_detail_app_version'),
        this.displayValue(this.session?.applicationVersion || ''))
      this.detailRow($r('app.string.management_detail_remote_endpoint'),
        this.displayValue(this.session?.remoteEndPoint || ''))
      this.detailRow($r('app.string.management_detail_play_method'),
        this.playMethodText(this.session?.playMethod || ''))
      this.detailRow($r('app.string.management_detail_volume'), this.volumeText())
      this.detailRow($r('app.string.management_detail_audio_stream'),
        this.streamIndexText(this.session?.audioStreamIndex ?? -1))
      this.detailRow($r('app.string.management_detail_subtitle_stream'),
        this.streamIndexText(this.session?.subtitleStreamIndex ?? -1))
      this.detailRow($r('app.string.management_detail_seekable'),
        this.supportText(this.session?.canSeek === true))
      this.detailRow($r('app.string.management_detail_media_control'),
        this.supportText(this.session?.supportsMediaControl === true))
      this.detailRow($r('app.string.management_detail_remote_control'),
        this.supportText(this.session?.supportsRemoteControl === true))
      this.detailRow($r('app.string.management_detail_last_activity'),
        this.formatDate(this.session?.lastActivityDate || ''), false)
    }
    .width('100%')
  }

  @Builder
  sessionDetailsGroup() {
    Column() {
      this.sessionDetailsBody()
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  sessionDetailsFeiniuGroup() {
    Column() {
      this.sessionDetailsBody()
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .backgroundColor($r('app.color.bg_1'))
  }

  @Builder
  sessionDetailsSurface() {
    if (this.useNativeMaterial()) {
      this.sessionDetailsGroup()
    } else {
      this.sessionDetailsFeiniuGroup()
    }
  }

  @Builder
  content() {
    Column({ space: 12 }) {
      this.sessionSummarySurface()
      this.sessionDetailsSurface()
    }
    .width('100%')
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
        Text(this.pendingSession ?
          (this.accessErrorCode === ManagementErrorCode.Offline ? $r('app.string.management_access_offline') :
            $r('app.string.management_permission_check_failed')) :
          $r('app.string.management_detail_unavailable'))
          .fontSize(14)
          .fontColor(this.secondaryTextColor())
          .textAlign(TextAlign.Center)
          .maxLines(3)
        if (this.pendingSession && this.accessErrorCode !== ManagementErrorCode.AuthenticationExpired) {
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
        ActionBar({ title: $r('app.string.management_session_detail_title') })
      }
      if (this.session) {
        Scroll() {
          this.content()
        }
        .layoutWeight(1)
        .width('100%')
        .scrollBar(BarState.Off)
        .padding({
          left: 16,
          right: 16,
          top: 12,
          bottom: this.appUIState.safeBottom + 24
        })
        .constraintSize({ maxWidth: 720 })
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
      title: $r('app.string.management_session_detail_title'),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}
