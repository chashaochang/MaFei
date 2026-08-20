import { ImageKnifeComponent } from '@ohos/imageknifepro';
import { ThemeStyle } from '../../../entity/ThemeStyle';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { ManagementSectionStatus } from '../ManagementModels';
import {
  ManagementPlaybackKind,
  ManagementSessionItem,
  ManagementSessionPresentation
} from './ManagementSessionModels';
import { ManagementSessionsUIState } from './ManagementSessionsUIState';

@ComponentV2
export struct ManagementSessionsDashboardSection {
  @Require
  @Param ui: ManagementSessionsUIState
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onOpenDevices: () => void = () => {
  }
  @Event onOpenSession: (session: ManagementSessionItem) => void = (_session: ManagementSessionItem) => {
  }

  private useNativeMaterial(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable)
  }

  private primaryTextColor(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable) ?
      $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable) ?
      $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private getString(name: string): string {
    return this.getUIContext().getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }

  private deviceLine(session: ManagementSessionItem): string {
    const deviceName = session.deviceName || this.getString('management_detail_unknown')
    return `${this.getString('management_detail_device')}: ${deviceName}`
  }

  private clientLine(session: ManagementSessionItem): string {
    const client = `${session.client} ${session.applicationVersion}`.trim()
    if (session.userName && client) {
      return `${session.userName} · ${client}`
    }
    return session.userName || client
  }

  private idleActivityLine(session: ManagementSessionItem): string {
    const formatted = ManagementSessionPresentation.formatActivityDate(session.lastActivityDate)
    const value = formatted || this.getString('management_detail_unknown')
    return `${this.getString('management_detail_last_activity')}: ${value}`
  }

  private formatTicks(ticks: number): string {
    const seconds = Math.max(0, Math.floor(ticks / 10000000))
    const minutes = Math.floor(seconds / 60)
    const remainder = seconds % 60
    return `${minutes}:${remainder.toString().padStart(2, '0')}`
  }

  private statusText(session: ManagementSessionItem): ResourceStr {
    if (!session.hasMedia) return $r('app.string.management_session_idle')
    if (session.playbackKind === ManagementPlaybackKind.Transcode) {
      return $r('app.string.management_session_transcoding')
    }
    return session.paused ? $r('app.string.management_session_paused') :
      $r('app.string.management_session_playing')
  }

  @Builder
  private header() {
    Row() {
      Text($r('app.string.management_active_sessions'))
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor())
        .layoutWeight(1)
      Button($r('app.string.management_devices_title'), { type: ButtonType.Normal })
        .height(32)
        .padding({ left: 12, right: 12 })
        .borderRadius(this.useNativeMaterial() ? 16 : 8)
        .fontSize(13)
        .fontColor($r('app.color.color_main'))
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveInteractive, this.useNativeMaterial()))
        .onClick(() => this.onOpenDevices())
    }
    .width('100%')
  }

  @Builder
  private sessionVisual(session: ManagementSessionItem) {
    if (session.hasMedia && session.imageUrl) {
      ImageKnifeComponent({
        imageKnifeOption: {
          loadSrc: session.imageUrl,
          objectFit: ImageFit.Cover,
          border: {
            radius: 8
          },
          placeholderSrc: $r('app.media.placeholder_video'),
          placeholderObjectFit: ImageFit.CENTER
        }
      })
        .width(64)
        .height(42)
        .accessibilityText(session.itemName)
    } else {
      SymbolGlyph(session.hasMedia ?
        (session.paused ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill')) :
        $r('sys.symbol.person_fill'))
        .fontSize(18)
        .fontColor([$r('app.color.color_main')])
        .width(42)
        .height(42)
        .backgroundColor(AppThemeSurfaceResolver.iconWellBackground(this.themeStyle))
        .borderRadius(12)
    }
  }

  @Builder
  private sessionRow(session: ManagementSessionItem) {
    Column({ space: 6 }) {
      Row({ space: 10 }) {
        this.sessionVisual(session)
        Column({ space: 3 }) {
          Text(session.hasMedia ? session.itemName :
            (session.userName || session.deviceName || this.getString('management_detail_unknown')))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (session.hasMedia && session.itemSubtitle) {
            Text(session.itemSubtitle)
              .fontSize(12)
              .fontColor(this.secondaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          Text(this.deviceLine(session))
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (this.clientLine(session)) {
            Text(this.clientLine(session))
              .fontSize(11)
              .fontColor(this.secondaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          if (!session.hasMedia) {
            Text(this.idleActivityLine(session))
              .fontSize(11)
              .fontColor(this.secondaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Text(this.statusText(session))
          .fontSize(11)
          .fontColor(session.paused ? this.secondaryTextColor() : $r('app.color.color_main'))
      }
      if (session.hasMedia && session.runtimeTicks > 0) {
        Row({ space: 8 }) {
          Progress({ value: session.positionTicks, total: session.runtimeTicks, type: ProgressType.Linear })
            .layoutWeight(1)
            .height(4)
            .color($r('app.color.color_main'))
          Text(`${this.formatTicks(session.positionTicks)} / ${this.formatTicks(session.runtimeTicks)}`)
            .fontSize(10)
            .fontColor(this.secondaryTextColor())
        }
      }
      if (session.playbackKind === ManagementPlaybackKind.Transcode) {
        Progress({ value: session.transcodingPercent, total: 100, type: ProgressType.Linear })
          .width('100%')
          .height(3)
          .color($r('app.color.status_warning'))
      }
    }
    .width('100%')
    .padding({ top: 8, bottom: 8 })
    .onClick(() => this.onOpenSession(session))
  }

  build() {
    Column({ space: 8 }) {
      this.header()
      if ((this.ui.status === ManagementSectionStatus.Content ||
        this.ui.status === ManagementSectionStatus.Error) && this.ui.sessions.length > 0) {
        ForEach(this.ui.sessions, (session: ManagementSessionItem) => {
          this.sessionRow(session)
        }, (session: ManagementSessionItem) => session.id)
        if (this.ui.stale) {
          Text($r('app.string.management_stale_result'))
            .fontSize(12)
            .fontColor($r('app.color.status_warning'))
        }
      } else if (this.ui.status === ManagementSectionStatus.Loading) {
        LoadingProgress().width(24).height(24).color($r('app.color.color_main'))
      } else {
        Text(this.ui.status === ManagementSectionStatus.Unsupported ?
          $r('app.string.management_section_unsupported') :
          $r('app.string.management_sessions_empty'))
          .fontSize(13)
          .fontColor(this.secondaryTextColor())
      }
    }
    .width('100%')
  }
}
