import { ImageKnifeComponent } from '@ohos/imageknifepro';
import promptAction from '@ohos.promptAction';
import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter';
import { RouterConsts } from '../../../common/RouterConsts';
import { UIConstants } from '../../../common/UIConstants';
import { ActionBar } from '../../../component/ActionBar';
import { AppRouteDestination } from '../../../component/AppRouteDestination';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../../theme/AppThemeOverlayPolicy';
import { AppThemeTokens } from '../../../theme/AppThemeTokens';
import { ManagementNativePagePolicy } from '../ManagementNativePagePolicy';
import { ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';
import {
  ManagementPlaybackKind,
  ManagementSessionAction,
  ManagementSessionActionResult,
  ManagementSessionItem,
  ManagementSessionPresentation
} from './ManagementSessionModels';
import { ManagementSessionsUIState } from './ManagementSessionsUIState';
import { ManagementSessionsViewModel } from './ManagementSessionsViewModel';

interface ManagementSessionStopDialogOptions extends promptAction.ShowDialogOptions {
  systemMaterial?: Object
}

@HMRouter({
  pageUrl: RouterConsts.ManagementSessionDetailPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementSessionDetailPage {
  @Local ui: ManagementSessionsUIState = new ManagementSessionsUIState(this.getUIContext())
  @Local vm: ManagementSessionsViewModel = new ManagementSessionsViewModel(this.ui)
  private lifecycleOwner: IHMLifecycleOwner | null = null
  private sessionId: string = ''
  private readonly contentScroller: Scroller = new Scroller()
  private pageShownCallback = (_context: HMLifecycleContext): void => {
    this.vm.initDetail(this.sessionId)
  }
  private pageHiddenCallback = (_context: HMLifecycleContext): void => {
    this.vm.stop()
  }

  aboutToAppear(): void {
    const param = HMRouterMgr.getCurrentParam() as object
    const value = param ? param['sessionId'] as string : ''
    this.sessionId = typeof value === 'string' ? value : ''
    this.lifecycleOwner = HMRouterMgr.getCurrentLifecycleOwner()
    if (this.lifecycleOwner) {
      this.lifecycleOwner.addObserver(HMLifecycleState.onShown, this.pageShownCallback)
      this.lifecycleOwner.addObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    } else {
      this.vm.initDetail(this.sessionId)
    }
  }

  aboutToDisappear(): void {
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onShown, this.pageShownCallback)
    this.lifecycleOwner?.removeObserver(HMLifecycleState.onHidden, this.pageHiddenCallback)
    this.lifecycleOwner = null
    this.vm.stop()
  }

  @Monitor('ui.permissionState')
  onPermissionChange(): void {
    if (this.ui.permissionState === ManagementPermissionState.Denied) {
      this.getUIContext().getPromptAction().showToast({
        message: $r('app.string.management_permission_denied')
      })
      HMRouterMgr.pop()
    }
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable)
  }

  private stopDialogMaterialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.Dangerous,
      this.vm.appUIState.themeStyle,
      this.vm.appUIState.systemMaterialAvailable,
      this.vm.appUIState.nativeThemeAvailable
    )
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private nativeTopSpacer(): number {
    return Math.max(0, this.vm.appUIState.safeTop) + UIConstants.ACTION_BAR_HEIGHT
  }

  private displayValue(value: string): ResourceStr {
    return value ? value : $r('app.string.management_detail_unknown')
  }

  private sessionStateText(item: ManagementSessionItem): ResourceStr {
    if (!item.hasMedia) return $r('app.string.management_session_idle')
    if (item.playbackKind === ManagementPlaybackKind.Transcode) {
      return $r('app.string.management_session_transcoding')
    }
    return item.paused ? $r('app.string.management_session_paused') :
      $r('app.string.management_session_playing')
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private playbackText(item: ManagementSessionItem): ResourceStr {
    if (item.playbackKind === ManagementPlaybackKind.Remux) {
      return $r('app.string.management_session_playback_remux')
    }
    if (item.playbackKind === ManagementPlaybackKind.DirectStream) {
      return $r('app.string.management_session_playback_direct_stream')
    }
    if (item.playbackKind === ManagementPlaybackKind.Transcode) {
      return $r('app.string.management_session_playback_transcode')
    }
    if (item.playbackKind === ManagementPlaybackKind.DirectPlay) {
      return $r('app.string.management_session_playback_direct_play')
    }
    return $r('app.string.management_session_idle')
  }

  private transcodeReasonsText(reasons: string[]): string {
    const values: string[] = []
    reasons.forEach((reason: string) => {
      const value = this.getString(ManagementSessionPresentation.transcodeReasonResourceName(reason))
      if (!values.includes(value)) {
        values.push(value)
      }
    })
    if (values.length === 0) {
      return this.getString('management_session_transcode_reason_other')
    }
    return values.join(' · ')
  }

  private formatTicks(ticks: number): string {
    const seconds = Math.max(0, Math.floor(ticks / 10000000))
    const hours = Math.floor(seconds / 3600)
    const minutes = Math.floor((seconds % 3600) / 60)
    const remainder = seconds % 60
    const minuteText = minutes.toString().padStart(2, '0')
    const secondText = remainder.toString().padStart(2, '0')
    return hours > 0 ? `${hours}:${minuteText}:${secondText}` : `${minutes}:${secondText}`
  }

  private showResult(result: ManagementSessionActionResult, success: ResourceStr): void {
    this.getUIContext().getPromptAction().showToast({
      message: result.ok ? success : (result.message ? this.getString(result.message) :
        $r('app.string.management_session_action_failed'))
    })
  }

  private async playPause(): Promise<void> {
    this.showResult(await this.vm.playPause(), $r('app.string.management_session_action_sent'))
  }

  private async sendMessage(): Promise<void> {
    this.showResult(await this.vm.sendMessage(), $r('app.string.management_session_message_sent'))
  }

  private confirmStop(item: ManagementSessionItem): void {
    const message = `${item.userName} · ${item.deviceName}\n${item.itemName}`
    if (this.stopDialogMaterialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      const options: ManagementSessionStopDialogOptions = {
        title: $r('app.string.management_session_stop_title'),
        message: message,
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_session_stop'), color: $r('app.color.main_red_color') }
        ],
        backgroundColor: this.useNativeMaterial() ?
          $r('sys.color.comp_background_primary') : $r('app.color.bg_1'),
        backgroundBlurStyle: BlurStyle.NONE,
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }
      this.getUIContext().getPromptAction().showDialog(options).then((response) => {
        if (response.index === 1) {
          this.vm.stopPlayback().then((result: ManagementSessionActionResult) =>
            this.showResult(result, $r('app.string.management_session_action_sent')))
        }
      }).catch(() => {
      })
    } else {
      this.getUIContext().getPromptAction().showDialog({
        title: $r('app.string.management_session_stop_title'),
        message: message,
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_session_stop'), color: $r('app.color.main_red_color') }
        ]
      }).then((response) => {
        if (response.index === 1) {
          this.vm.stopPlayback().then((result: ManagementSessionActionResult) =>
            this.showResult(result, $r('app.string.management_session_action_sent')))
        }
      }).catch(() => {
      })
    }
  }

  @Builder
  private detailRow(label: ResourceStr, value: ResourceStr, showDivider: boolean = true) {
    Column() {
      Row({ space: 16 }) {
        Text(label)
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
          .constraintSize({ minWidth: 104 })
        Text(value)
          .fontSize(13)
          .fontColor(this.primaryTextColor())
          .layoutWeight(1)
          .textAlign(TextAlign.End)
          .maxLines(3)
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
  private staleNotice(message: ResourceStr) {
    Row({ space: 8 }) {
      Text(message)
        .fontSize(12)
        .fontColor($r('app.color.status_warning'))
        .layoutWeight(1)
      Button($r('app.string.management_retry'), { type: ButtonType.Normal })
        .height(32)
        .padding({ left: 12, right: 12 })
        .borderRadius(this.useNativeMaterial() ? 16 : 8)
        .fontSize(12)
        .fontColor($r('app.color.color_main'))
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
        .onClick(() => this.vm.refresh())
    }
    .width('100%')
    .padding(12)
    .borderRadius(8)
    .backgroundColor(this.useNativeMaterial() ?
      $r('sys.color.comp_background_secondary') : $r('app.color.component_bg'))
  }

  @Builder
  private nativeStatusGroup(item: ManagementSessionItem) {
    Column({ space: 12 }) {
      if (item.imageUrl) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: item.imageUrl,
            objectFit: ImageFit.Cover,
            border: { radius: 8 },
            placeholderSrc: $r('app.media.placeholder_video'),
            placeholderObjectFit: ImageFit.CENTER
          }
        })
          .width('100%')
          .height(190)
          .accessibilityText(item.itemName)
      }
      Row({ space: 12 }) {
        Column({ space: 4 }) {
          Text(item.hasMedia ? this.displayValue(item.itemName) : this.displayValue(item.deviceName))
            .fontSize(20)
            .fontWeight(FontWeight.Bold)
            .fontColor(this.primaryTextColor())
            .maxLines(2)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (item.itemSubtitle) {
            Text(item.itemSubtitle)
              .fontSize(13)
              .fontColor(this.secondaryTextColor())
              .maxLines(2)
          }
          Text(`${item.userName} · ${item.deviceName}`)
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .maxLines(2)
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Text(this.sessionStateText(item))
          .fontSize(12)
          .fontWeight(FontWeight.Medium)
          .fontColor(item.hasMedia && !item.paused ?
            $r('app.color.status_success') : this.secondaryTextColor())
      }
      .width('100%')
      if (item.hasMedia && item.runtimeTicks > 0) {
        Progress({ value: item.positionTicks, total: item.runtimeTicks, type: ProgressType.Linear })
          .width('100%')
          .height(5)
          .color($r('app.color.color_main'))
          .backgroundColor($r('sys.color.comp_background_tertiary'))
        Text(`${this.formatTicks(item.positionTicks)} / ${this.formatTicks(item.runtimeTicks)}`)
          .fontSize(11)
          .fontColor(this.secondaryTextColor())
          .width('100%')
          .textAlign(TextAlign.End)
      }
    }
    .width('100%')
    .padding(18)
    .borderRadius(8)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private nativePlaybackGroup(item: ManagementSessionItem) {
    Column() {
      this.detailRow($r('app.string.management_session_playback_method'), this.playbackText(item))
      if (item.hasMedia && item.runtimeTicks > 0) {
        this.detailRow($r('app.string.management_detail_progress'),
          `${this.formatTicks(item.positionTicks)} / ${this.formatTicks(item.runtimeTicks)}`)
      }
      this.detailRow($r('app.string.management_detail_last_activity'),
        this.displayValue(ManagementSessionPresentation.formatActivityDate(item.lastActivityDate)), false)
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .borderRadius(8)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private nativeTranscodeGroup(item: ManagementSessionItem) {
    Column() {
      this.detailRow($r('app.string.management_session_bitrate'),
        item.bitrate > 0 ? item.bitrate.toString() : $r('app.string.management_detail_unknown'))
      this.detailRow($r('app.string.management_session_framerate'),
        item.framerate > 0 ? item.framerate.toString() : $r('app.string.management_detail_unknown'))
      this.detailRow($r('app.string.management_session_container'), this.displayValue(item.container))
      this.detailRow($r('app.string.management_session_video_codec'), this.displayValue(item.videoCodec))
      this.detailRow($r('app.string.management_session_audio_codec'), this.displayValue(item.audioCodec))
      this.detailRow($r('app.string.management_session_resolution'), this.displayValue(item.resolution))
      this.detailRow($r('app.string.management_session_transcode_reasons'),
        this.transcodeReasonsText(item.transcodeReasons), false)
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .borderRadius(8)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private nativeConnectionGroup(item: ManagementSessionItem) {
    Column() {
      this.detailRow($r('app.string.management_detail_user'), this.displayValue(item.userName))
      this.detailRow($r('app.string.management_detail_device'), this.displayValue(item.deviceName))
      this.detailRow($r('app.string.management_detail_device_type'), this.displayValue(item.deviceType))
      this.detailRow($r('app.string.management_detail_client'),
        this.displayValue(`${item.client} ${item.applicationVersion}`.trim()))
      this.detailRow($r('app.string.management_detail_remote_endpoint'),
        this.displayValue(item.remoteEndPoint), false)
    }
    .width('100%')
    .padding({ left: 16, right: 16 })
    .borderRadius(8)
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private nativeContent(item: ManagementSessionItem) {
    Column({ space: 12 }) {
      this.nativeStatusGroup(item)
      this.nativePlaybackGroup(item)
      if (item.playbackKind === ManagementPlaybackKind.Transcode) {
        this.nativeTranscodeGroup(item)
      }
      this.nativeConnectionGroup(item)
    }
    .width('100%')
  }

  @Builder
  private legacyContent(item: ManagementSessionItem) {
    Column({ space: 12 }) {
      Column({ space: 8 }) {
        if (item.imageUrl) {
          ImageKnifeComponent({
            imageKnifeOption: {
              loadSrc: item.imageUrl,
              objectFit: ImageFit.Cover,
              border: { radius: 8 },
              placeholderSrc: $r('app.media.placeholder_video'),
              placeholderObjectFit: ImageFit.CENTER
            }
          })
            .width('100%')
            .height(180)
            .accessibilityText(item.itemName)
        }
        Text(item.hasMedia ? item.itemName : item.deviceName)
          .fontSize(20).fontWeight(FontWeight.Bold).fontColor(this.primaryTextColor())
        if (item.itemSubtitle) {
          Text(item.itemSubtitle).fontSize(13).fontColor(this.secondaryTextColor())
        }
        Text(`${item.userName} · ${item.deviceName} · ${item.client} ${item.applicationVersion}`)
          .fontSize(12).fontColor(this.secondaryTextColor()).maxLines(2)
        if (item.hasMedia && item.runtimeTicks > 0) {
          Progress({ value: item.positionTicks, total: item.runtimeTicks, type: ProgressType.Linear })
            .width('100%').height(5).color($r('app.color.color_main'))
          Text(`${this.formatTicks(item.positionTicks)} / ${this.formatTicks(item.runtimeTicks)}`)
            .fontSize(11).fontColor(this.secondaryTextColor())
        }
      }
      .width('100%')
      .padding(18)
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
      Column() {
        this.detailRow($r('app.string.management_session_playback_method'), this.playbackText(item))
        this.detailRow($r('app.string.management_detail_last_activity'),
          this.displayValue(ManagementSessionPresentation.formatActivityDate(item.lastActivityDate)),
          item.playbackKind === ManagementPlaybackKind.Transcode)
        if (item.playbackKind === ManagementPlaybackKind.Transcode) {
          this.detailRow($r('app.string.management_session_bitrate'), item.bitrate.toString())
          this.detailRow($r('app.string.management_session_framerate'), item.framerate.toString())
          this.detailRow($r('app.string.management_session_container'), item.container)
          this.detailRow($r('app.string.management_session_video_codec'), item.videoCodec)
          this.detailRow($r('app.string.management_session_audio_codec'), item.audioCodec)
          this.detailRow($r('app.string.management_session_resolution'), item.resolution)
          this.detailRow($r('app.string.management_session_transcode_reasons'),
            this.transcodeReasonsText(item.transcodeReasons), false)
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor($r('app.color.bg_1'))
      if (this.showRemoteBar(item)) {
        Column({ space: 10 }) {
          if (item.hasMedia) {
            Row({ space: 10 }) {
              Button(item.paused ? $r('app.string.management_session_resume') :
                $r('app.string.management_session_pause'), { type: ButtonType.Normal })
                .layoutWeight(1)
                .height(42)
                .enabled(this.ui.pendingAction === ManagementSessionAction.None)
                .onClick(() => this.playPause())
              Button($r('app.string.management_session_stop'), { type: ButtonType.Normal })
                .layoutWeight(1)
                .height(42)
                .fontColor($r('app.color.main_red_color'))
                .enabled(this.ui.pendingAction === ManagementSessionAction.None)
                .onClick(() => this.confirmStop(item))
            }
          }
          Row({ space: 8 }) {
            TextInput({
              text: this.ui.messageDraft,
              placeholder: $r('app.string.management_session_message_placeholder')
            })
              .layoutWeight(1)
              .height(42)
              .onChange((value: string) => this.ui.messageDraft = value)
            Button($r('app.string.management_session_send'), { type: ButtonType.Normal })
              .height(42)
              .enabled(this.ui.pendingAction === ManagementSessionAction.None &&
                this.ui.messageDraft.trim().length > 0)
              .onClick(() => this.sendMessage())
          }
        }
        .width('100%')
        .padding(16)
        .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
        .backgroundColor($r('app.color.bg_1'))
      }
    }
    .width('100%')
  }

  @Builder
  private sessionContent(item: ManagementSessionItem, native: boolean) {
    Column({ space: 12 }) {
      if (this.ui.stale) {
        this.staleNotice($r('app.string.management_stale_result'))
      }
      if (native) {
        this.nativeContent(item)
      } else {
        this.legacyContent(item)
      }
    }
    .width('100%')
  }

  private showRemoteBar(item: ManagementSessionItem): boolean {
    return item.controllable && !this.ui.ended
  }

  @Builder
  private remoteActionBar(item: ManagementSessionItem) {
    Row({ space: 8 }) {
      if (item.hasMedia) {
        Button(item.paused ? $r('app.string.management_session_resume') :
          $r('app.string.management_session_pause'), { type: ButtonType.Normal })
          .height(42)
          .padding({ left: 14, right: 14 })
          .borderRadius(21)
          .fontColor(this.primaryTextColor())
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveControl, true))
          .enabled(this.ui.pendingAction === ManagementSessionAction.None)
          .onClick(() => this.playPause())
      }
      TextInput({
        text: this.ui.messageDraft,
        placeholder: $r('app.string.management_session_message_placeholder')
      })
        .layoutWeight(1)
        .height(42)
        .borderRadius(8)
        .backgroundColor($r('sys.color.comp_background_tertiary'))
        .onChange((value: string) => this.ui.messageDraft = value)
      Button($r('app.string.management_session_send'), { type: ButtonType.Normal })
        .height(42)
        .padding({ left: 14, right: 14 })
        .borderRadius(21)
        .fontColor($r('app.color.on_primary'))
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.PrimaryInteractive, true))
        .enabled(this.ui.pendingAction === ManagementSessionAction.None &&
          this.ui.messageDraft.trim().length > 0)
        .onClick(() => this.sendMessage())
      if (item.hasMedia) {
        Button($r('app.string.management_session_stop'), { type: ButtonType.Normal })
          .height(42)
          .padding({ left: 12, right: 12 })
          .borderRadius(21)
          .fontColor($r('app.color.main_red_color'))
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveControl, true))
          .enabled(this.ui.pendingAction === ManagementSessionAction.None)
          .onClick(() => this.confirmStop(item))
      }
    }
    .width('100%')
    .padding({ left: 16, right: 16, top: 10, bottom: this.vm.appUIState.safeBottom + 10 })
    .attributeModifier(AppThemeSurfaceResolver.modifier(AppThemeMaterialRole.Floating, true))
  }

  @Builder
  private nativeDetailPage(item: ManagementSessionItem) {
    Stack({ alignContent: Alignment.Bottom }) {
      Scroll(this.contentScroller) {
        Column({ space: 12 }) {
          Blank().height(this.nativeTopSpacer())
          this.sessionContent(item, true)
        }
        .width('100%')
        .constraintSize({ maxWidth: 760 })
        .padding({
          left: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
          right: this.vm.appUIState.deviceWidth >= 600 ? 24 : 16,
          bottom: ManagementNativePagePolicy.contentBottomPadding(
            this.vm.appUIState.safeBottom + 24, this.showRemoteBar(item))
        })
      }
      .width('100%')
      .height('100%')
      .scrollBar(BarState.Auto)
      if (this.showRemoteBar(item)) this.remoteActionBar(item)
    }
    .layoutWeight(1)
    .width('100%')
  }

  @Builder
  private legacyDetailPage(item: ManagementSessionItem) {
    Scroll() {
      this.sessionContent(item, false)
    }
    .layoutWeight(1)
    .width('100%')
    .scrollBar(BarState.Off)
    .padding({ left: 16, right: 16, top: 12, bottom: this.vm.appUIState.safeBottom + 24 })
  }

  @Builder
  private unavailableContent() {
    Column({ space: 12 }) {
      Text(this.ui.ended ? $r('app.string.management_session_ended') :
        $r('app.string.management_detail_unavailable'))
        .fontSize(16)
        .fontColor(this.secondaryTextColor())
      if (this.ui.ended) {
        Button($r('app.string.management_session_return_dashboard'), { type: ButtonType.Normal })
          .height(42)
          .padding({ left: 22, right: 22 })
          .borderRadius(this.useNativeMaterial() ? 21 : 10)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
          .onClick(() => HMRouterMgr.pop())
      } else {
        Button($r('app.string.management_retry'), { type: ButtonType.Normal })
          .height(42)
          .padding({ left: 22, right: 22 })
          .borderRadius(this.useNativeMaterial() ? 21 : 10)
          .fontColor($r('app.color.on_primary'))
          .backgroundColor(this.useNativeMaterial() ? Color.Transparent : $r('app.color.color_main'))
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.PrimaryInteractive, this.useNativeMaterial()))
          .onClick(() => this.vm.refresh())
      }
    }
    .layoutWeight(1)
    .justifyContent(FlexAlign.Center)
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({ title: $r('app.string.management_session_detail_title') })
      }
      if (this.ui.detail) {
        if (showLegacyActionBar) {
          this.legacyDetailPage(this.ui.detail!)
        } else {
          this.nativeDetailPage(this.ui.detail!)
        }
      } else if (this.ui.status === ManagementSectionStatus.Loading) {
        LoadingProgress().layoutWeight(1).width(32).height(32).color($r('app.color.color_main'))
      } else {
        this.unavailableContent()
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.management_session_detail_title'),
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
