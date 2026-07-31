import { ImageKnifeComponent } from '@ohos/imageknifepro';
import {
  HMLifecycleContext,
  HMLifecycleState,
  HMRouter,
  HMRouterMgr,
  IHMLifecycleOwner
} from '@hadss/hmrouter';
import { deviceInfo } from '@kit.BasicServicesKit';
import { RouterConsts } from '../../../common/RouterConsts';
import { ActionBar } from '../../../component/ActionBar';
import { AppRouteDestination } from '../../../component/AppRouteDestination';
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { AppThemeTokens } from '../../../theme/AppThemeTokens';
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

  private primaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeTokens.isNative(this.vm.appUIState.themeStyle) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
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
    if (deviceInfo.sdkApiVersion >= 26) {
      this.getUIContext().getPromptAction().showDialog({
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
      }).then((response) => {
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
  private detailRow(label: ResourceStr, value: ResourceStr) {
    Row({ space: 16 }) {
      Text(label).fontSize(13).fontColor(this.secondaryTextColor()).constraintSize({ minWidth: 104 })
      Text(value).fontSize(13).fontColor(this.primaryTextColor()).layoutWeight(1)
        .textAlign(TextAlign.End).maxLines(3).textOverflow({ overflow: TextOverflow.Ellipsis })
    }
    .width('100%')
    .constraintSize({ minHeight: 46 })
  }

  @Builder
  private content(item: ManagementSessionItem) {
    Column({ space: 12 }) {
      if (this.ui.stale) {
        Row({ space: 8 }) {
          Text($r('app.string.management_stale_result'))
            .fontSize(12)
            .fontColor($r('app.color.status_warning'))
            .layoutWeight(1)
          Button($r('app.string.management_retry'), { type: ButtonType.Normal })
            .height(32)
            .padding({ left: 12, right: 12 })
            .fontSize(12)
            .fontColor($r('app.color.color_main'))
            .backgroundColor(Color.Transparent)
            .onClick(() => this.vm.refresh())
        }
        .width('100%')
        .padding(12)
        .borderRadius(8)
        .backgroundColor(this.useNativeMaterial() ?
          $r('sys.color.comp_background_secondary') : $r('app.color.component_bg'))
      }
      Column({ space: 8 }) {
        if (item.imageUrl) {
          ImageKnifeComponent({
            imageKnifeOption: {
              loadSrc: item.imageUrl,
              objectFit: ImageFit.Cover,
              border: {
                radius: 8
              },
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
      .backgroundColor(this.useNativeMaterial() ?
        AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))

      Column() {
        this.detailRow($r('app.string.management_session_playback_method'), this.playbackText(item))
        this.detailRow($r('app.string.management_detail_last_activity'),
          ManagementSessionPresentation.formatActivityDate(item.lastActivityDate) ||
          $r('app.string.management_detail_unknown'))
        if (item.playbackKind === ManagementPlaybackKind.Transcode) {
          this.detailRow($r('app.string.management_session_bitrate'), item.bitrate.toString())
          this.detailRow($r('app.string.management_session_framerate'), item.framerate.toString())
          this.detailRow($r('app.string.management_session_container'), item.container)
          this.detailRow($r('app.string.management_session_video_codec'), item.videoCodec)
          this.detailRow($r('app.string.management_session_audio_codec'), item.audioCodec)
          this.detailRow($r('app.string.management_session_resolution'), item.resolution)
          this.detailRow($r('app.string.management_session_transcode_reasons'),
            this.transcodeReasonsText(item.transcodeReasons))
        }
      }
      .width('100%')
      .padding({ left: 16, right: 16 })
      .borderRadius(AppThemeTokens.groupRadius(this.vm.appUIState.themeStyle))
      .backgroundColor(this.useNativeMaterial() ?
        AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))

      if (item.controllable && !this.ui.ended) {
        Column({ space: 10 }) {
          if (item.hasMedia) {
            Row({ space: 10 }) {
              Button(item.paused ? $r('app.string.management_session_resume') :
                $r('app.string.management_session_pause'), { type: ButtonType.Normal })
                .layoutWeight(1).height(42).enabled(this.ui.pendingAction === ManagementSessionAction.None)
                .onClick(() => this.playPause())
              Button($r('app.string.management_session_stop'), { type: ButtonType.Normal })
                .layoutWeight(1).height(42).fontColor($r('app.color.main_red_color'))
                .enabled(this.ui.pendingAction === ManagementSessionAction.None)
                .onClick(() => this.confirmStop(item))
            }
          }
          Row({ space: 8 }) {
            TextInput({
              text: this.ui.messageDraft,
              placeholder: $r('app.string.management_session_message_placeholder')
            })
              .layoutWeight(1).height(42)
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
        .backgroundColor(this.useNativeMaterial() ?
          AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
      }
    }
    .width('100%')
  }

  @Builder
  private pageContent(showLegacyActionBar: boolean) {
    Column() {
      if (showLegacyActionBar) {
        ActionBar({ title: $r('app.string.management_session_detail_title') })
      }
      if (this.ui.detail) {
        Scroll() {
          this.content(this.ui.detail!)
        }
        .layoutWeight(1).width('100%').scrollBar(BarState.Off)
        .padding({ left: 16, right: 16, top: 12, bottom: this.vm.appUIState.safeBottom + 24 })
      } else if (this.ui.status === ManagementSectionStatus.Loading) {
        LoadingProgress().layoutWeight(1).width(32).height(32).color($r('app.color.color_main'))
      } else {
        Column({ space: 12 }) {
          Text(this.ui.ended ? $r('app.string.management_session_ended') :
            $r('app.string.management_detail_unavailable'))
            .fontSize(16).fontColor(this.secondaryTextColor())
          if (this.ui.ended) {
            Button($r('app.string.management_session_return_dashboard'), { type: ButtonType.Normal })
              .height(42)
              .onClick(() => HMRouterMgr.pop())
          } else {
            Button($r('app.string.management_retry'), { type: ButtonType.Normal })
              .height(42)
              .onClick(() => this.vm.refresh())
          }
        }
        .layoutWeight(1).justifyContent(FlexAlign.Center)
      }
    }
    .width('100%').height('100%')
    .backgroundColor(showLegacyActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.vm.appUIState.themeStyle, this.vm.appUIState.nativeThemeAvailable) : Color.Transparent)
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
