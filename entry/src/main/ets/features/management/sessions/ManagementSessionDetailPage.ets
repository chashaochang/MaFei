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
import { AppRouteDestination, AppRouteDestinationOptions } from '../../../component/AppRouteDestination';
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
import { ManagementDetailRow } from '../ManagementDetailRow';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

interface ManagementSessionStopDialogOptions extends promptAction.ShowDialogOptions {
  systemMaterial?: Object
}

@ComponentV2
struct ManagementSessionDetailContent {
  @Param showLegacyActionBar: boolean = false
  @Param contentScroller: Scroller = new Scroller()
  @Local ui: ManagementSessionsUIState = new ManagementSessionsUIState(this.getUIContext())
  @Local vm: ManagementSessionsViewModel = new ManagementSessionsViewModel(this.ui)
  private lifecycleOwner: IHMLifecycleOwner | null = null
  private sessionId: string = ''
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










  private showRemoteBar(item: ManagementSessionItem): boolean {
    return item.controllable && !this.ui.ended
  }





  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: $r('app.string.management_session_detail_title') })
      }
      if (this.ui.detail) {
        Stack({ alignContent: Alignment.Bottom }) {
          Scroll(this.contentScroller) {
            Column({ space: 12 }) {
              if (!this.showLegacyActionBar) {
                Blank().height(this.nativeTopSpacer())
              }
              Column({ space: 12 }) {
                if (this.ui.stale) {
                  Row({ space: 8 }) {
                    Text(($r('app.string.management_stale_result')))
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
                if ((!this.showLegacyActionBar)) {
                  Column({ space: 12 }) {
                    Column({ space: 12 }) {
                      if ((((this.ui.detail!))).imageUrl) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: (((this.ui.detail!))).imageUrl,
                            objectFit: ImageFit.Cover,
                            border: { radius: 8 },
                            placeholderSrc: $r('app.media.placeholder_video'),
                            placeholderObjectFit: ImageFit.CENTER
                          }
                        })
                          .width('100%')
                          .height(190)
                          .accessibilityText((((this.ui.detail!))).itemName)
                      }
                      Row({ space: 12 }) {
                        Column({ space: 4 }) {
                          Text((((this.ui.detail!))).hasMedia ? this.displayValue((((this.ui.detail!))).itemName) : this.displayValue((((this.ui.detail!))).deviceName))
                            .fontSize(20)
                            .fontWeight(FontWeight.Bold)
                            .fontColor(this.primaryTextColor())
                            .maxLines(2)
                            .textOverflow({ overflow: TextOverflow.Ellipsis })
                          if ((((this.ui.detail!))).itemSubtitle) {
                            Text((((this.ui.detail!))).itemSubtitle)
                              .fontSize(13)
                              .fontColor(this.secondaryTextColor())
                              .maxLines(2)
                          }
                          Text(`${(this.ui.detail!).userName} · ${(this.ui.detail!).deviceName}`)
                            .fontSize(12)
                            .fontColor(this.secondaryTextColor())
                            .maxLines(2)
                        }
                        .layoutWeight(1)
                        .alignItems(HorizontalAlign.Start)
                        Text(this.sessionStateText((((this.ui.detail!)))))
                          .fontSize(12)
                          .fontWeight(FontWeight.Medium)
                          .fontColor((((this.ui.detail!))).hasMedia && !(((this.ui.detail!))).paused ?
                            $r('app.color.status_success') : this.secondaryTextColor())
                      }
                      .width('100%')
                      if ((((this.ui.detail!))).hasMedia && (((this.ui.detail!))).runtimeTicks > 0) {
                        Progress({ value: (((this.ui.detail!))).positionTicks, total: (((this.ui.detail!))).runtimeTicks, type: ProgressType.Linear })
                          .width('100%')
                          .height(5)
                          .color($r('app.color.color_main'))
                          .backgroundColor($r('sys.color.comp_background_tertiary'))
                        Text(`${this.formatTicks((this.ui.detail!).positionTicks)} / ` +
                          `${this.formatTicks((this.ui.detail!).runtimeTicks)}`)
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
                    Column() {
                      ManagementDetailRow({
                        label: $r('app.string.management_session_playback_method'),
                        value: this.playbackText((((this.ui.detail!)))),
                        showDivider: true,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                      if ((((this.ui.detail!))).hasMedia && (((this.ui.detail!))).runtimeTicks > 0) {
                        ManagementDetailRow({
                          label: $r('app.string.management_detail_progress'),
                          value: `${this.formatTicks((this.ui.detail!).positionTicks)} / ` +
                            `${this.formatTicks((this.ui.detail!).runtimeTicks)}`,
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                      }
                      ManagementDetailRow({
                        label: $r('app.string.management_detail_last_activity'),
                        value: this.displayValue(ManagementSessionPresentation.formatActivityDate((((this.ui.detail!))).lastActivityDate)),
                        showDivider: false,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                    }
                    .width('100%')
                    .padding({ left: 16, right: 16 })
                    .borderRadius(8)
                    .clip(true)
                    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
                    if (((this.ui.detail!)).playbackKind === ManagementPlaybackKind.Transcode) {
                      Column() {
                        ManagementDetailRow({
                          label: $r('app.string.management_session_bitrate'),
                          value: (((this.ui.detail!))).bitrate > 0 ? (((this.ui.detail!))).bitrate.toString() : $r('app.string.management_detail_unknown'),
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_framerate'),
                          value: (((this.ui.detail!))).framerate > 0 ? (((this.ui.detail!))).framerate.toString() : $r('app.string.management_detail_unknown'),
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_container'),
                          value: this.displayValue((((this.ui.detail!))).container),
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_video_codec'),
                          value: this.displayValue((((this.ui.detail!))).videoCodec),
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_audio_codec'),
                          value: this.displayValue((((this.ui.detail!))).audioCodec),
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_resolution'),
                          value: this.displayValue((((this.ui.detail!))).resolution),
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_transcode_reasons'),
                          value: this.transcodeReasonsText((((this.ui.detail!))).transcodeReasons),
                          showDivider: false,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                      }
                      .width('100%')
                      .padding({ left: 16, right: 16 })
                      .borderRadius(8)
                      .clip(true)
                      .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
                    }
                    Column() {
                      ManagementDetailRow({
                        label: $r('app.string.management_detail_user'),
                        value: this.displayValue((((this.ui.detail!))).userName),
                        showDivider: true,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                      ManagementDetailRow({
                        label: $r('app.string.management_detail_device'),
                        value: this.displayValue((((this.ui.detail!))).deviceName),
                        showDivider: true,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                      ManagementDetailRow({
                        label: $r('app.string.management_detail_device_type'),
                        value: this.displayValue((((this.ui.detail!))).deviceType),
                        showDivider: true,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                      ManagementDetailRow({
                        label: $r('app.string.management_detail_client'),
                        value: this.displayValue(
                          `${(this.ui.detail!).client} ${(this.ui.detail!).applicationVersion}`.trim()),
                        showDivider: true,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                      ManagementDetailRow({
                        label: $r('app.string.management_detail_remote_endpoint'),
                        value: this.displayValue((((this.ui.detail!))).remoteEndPoint),
                        showDivider: false,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                    }
                    .width('100%')
                    .padding({ left: 16, right: 16 })
                    .borderRadius(8)
                    .clip(true)
                    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
                  }
                  .width('100%')
                } else {
                  Column({ space: 12 }) {
                    Column({ space: 8 }) {
                      if (((this.ui.detail!)).imageUrl) {
                        ImageKnifeComponent({
                          imageKnifeOption: {
                            loadSrc: ((this.ui.detail!)).imageUrl,
                            objectFit: ImageFit.Cover,
                            border: { radius: 8 },
                            placeholderSrc: $r('app.media.placeholder_video'),
                            placeholderObjectFit: ImageFit.CENTER
                          }
                        })
                          .width('100%')
                          .height(180)
                          .accessibilityText(((this.ui.detail!)).itemName)
                      }
                      Text(((this.ui.detail!)).hasMedia ? ((this.ui.detail!)).itemName : ((this.ui.detail!)).deviceName)
                        .fontSize(20).fontWeight(FontWeight.Bold).fontColor(this.primaryTextColor())
                      if (((this.ui.detail!)).itemSubtitle) {
                        Text(((this.ui.detail!)).itemSubtitle).fontSize(13).fontColor(this.secondaryTextColor())
                      }
                      Text(`${(this.ui.detail!).userName} · ${(this.ui.detail!).deviceName} · ` +
                        `${(this.ui.detail!).client} ${(this.ui.detail!).applicationVersion}`)
                        .fontSize(12).fontColor(this.secondaryTextColor()).maxLines(2)
                      if (((this.ui.detail!)).hasMedia && ((this.ui.detail!)).runtimeTicks > 0) {
                        Progress({ value: ((this.ui.detail!)).positionTicks, total: ((this.ui.detail!)).runtimeTicks, type: ProgressType.Linear })
                          .width('100%').height(5).color($r('app.color.color_main'))
                        Text(`${this.formatTicks((this.ui.detail!).positionTicks)} / ` +
                          `${this.formatTicks((this.ui.detail!).runtimeTicks)}`)
                          .fontSize(11).fontColor(this.secondaryTextColor())
                      }
                    }
                    .width('100%')
                    .padding(18)
                    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                    .backgroundColor($r('app.color.bg_1'))
                    Column() {
                      ManagementDetailRow({
                        label: $r('app.string.management_session_playback_method'),
                        value: this.playbackText(((this.ui.detail!))),
                        showDivider: true,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                      ManagementDetailRow({
                        label: $r('app.string.management_detail_last_activity'),
                        value: this.displayValue(ManagementSessionPresentation.formatActivityDate(((this.ui.detail!)).lastActivityDate)),
                        showDivider: ((this.ui.detail!)).playbackKind === ManagementPlaybackKind.Transcode,
                        labelMinWidth: 104,
                        valueMaxLines: 3,
                        primaryTextColor: this.primaryTextColor(),
                        secondaryTextColor: this.secondaryTextColor(),
                        dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                      })
                      if (((this.ui.detail!)).playbackKind === ManagementPlaybackKind.Transcode) {
                        ManagementDetailRow({
                          label: $r('app.string.management_session_bitrate'),
                          value: ((this.ui.detail!)).bitrate.toString(),
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_framerate'),
                          value: ((this.ui.detail!)).framerate.toString(),
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_container'),
                          value: ((this.ui.detail!)).container,
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_video_codec'),
                          value: ((this.ui.detail!)).videoCodec,
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_audio_codec'),
                          value: ((this.ui.detail!)).audioCodec,
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_resolution'),
                          value: ((this.ui.detail!)).resolution,
                          showDivider: true,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                        ManagementDetailRow({
                          label: $r('app.string.management_session_transcode_reasons'),
                          value: this.transcodeReasonsText(((this.ui.detail!)).transcodeReasons),
                          showDivider: false,
                          labelMinWidth: 104,
                          valueMaxLines: 3,
                          primaryTextColor: this.primaryTextColor(),
                          secondaryTextColor: this.secondaryTextColor(),
                          dividerColor: this.useNativeMaterial() ? $r("sys.color.comp_divider") : $r("app.color.border")
                        })
                      }
                    }
                    .width('100%')
                    .padding({ left: 16, right: 16 })
                    .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
                    .backgroundColor($r('app.color.bg_1'))
                    if (this.showRemoteBar(((this.ui.detail!)))) {
                      Column({ space: 10 }) {
                        if (((this.ui.detail!)).hasMedia) {
                          Row({ space: 10 }) {
                            Button(((this.ui.detail!)).paused ? $r('app.string.management_session_resume') :
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
                              .onClick(() => this.confirmStop(((this.ui.detail!))))
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
              }
              .width('100%')
            }
            .width('100%')
            .constraintSize({ maxWidth: this.showLegacyActionBar ? '100%' : 760 })
            .padding({
              left: this.showLegacyActionBar ? 16 : (this.vm.appUIState.deviceWidth >= 600 ? 24 : 16),
              right: this.showLegacyActionBar ? 16 : (this.vm.appUIState.deviceWidth >= 600 ? 24 : 16),
              top: this.showLegacyActionBar ? 12 : 0,
              bottom: this.showLegacyActionBar ? this.vm.appUIState.safeBottom + 24 :
                ManagementNativePagePolicy.contentBottomPadding(
                  this.vm.appUIState.safeBottom + 24, this.showRemoteBar(this.ui.detail!))
            })
          }
          .width('100%')
          .height('100%')
          .scrollBar(this.showLegacyActionBar ? BarState.Off : BarState.Auto)
          if (!this.showLegacyActionBar && this.showRemoteBar(this.ui.detail!)) {
            Row({ space: 8 }) {
              if ((this.ui.detail!).hasMedia) {
                Button((this.ui.detail!).paused ? $r('app.string.management_session_resume') :
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
              if ((this.ui.detail!).hasMedia) {
                Button($r('app.string.management_session_stop'), { type: ButtonType.Normal })
                  .height(42)
                  .padding({ left: 12, right: 12 })
                  .borderRadius(21)
                  .fontColor($r('app.color.main_red_color'))
                  .backgroundColor(Color.Transparent)
                  .attributeModifier(AppThemeSurfaceResolver.modifier(
                    AppThemeMaterialRole.AdaptiveControl, true))
                  .enabled(this.ui.pendingAction === ManagementSessionAction.None)
                  .onClick(() => this.confirmStop((this.ui.detail!)))
              }
            }
            .width('100%')
            .padding({ left: 16, right: 16, top: 10, bottom: this.vm.appUIState.safeBottom + 10 })
            .attributeModifier(AppThemeSurfaceResolver.modifier(AppThemeMaterialRole.Floating, true))
          }
        }
        .layoutWeight(1)
        .width('100%')
      } else if (this.ui.status === ManagementSectionStatus.Loading) {
        LoadingProgress().layoutWeight(1).width(32).height(32).color($r('app.color.color_main'))
      } else {
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
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
  }
}

@HMRouter({
  pageUrl: RouterConsts.ManagementSessionDetailPage,
  useNavDst: true
})
@ComponentV2
export struct ManagementSessionDetailPage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private readonly contentScroller: Scroller = new Scroller()

  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.management_session_detail_title'),
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
              ManagementSessionDetailContent({
                showLegacyActionBar: false,
                contentScroller: this.contentScroller
              })
            } else {
              ManagementSessionDetailContent({
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
        ManagementSessionDetailContent({
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
