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
struct ManagementSessionsHeader {
  @Require @Param primaryTextColor: ResourceColor
  @Require @Param nativeMaterial: boolean
  @Event onOpenDevices: () => void = () => {}

  build() {
    Row() {
      Text($r('app.string.management_active_sessions'))
        .fontSize(18)
        .fontWeight(FontWeight.Bold)
        .fontColor(this.primaryTextColor)
        .layoutWeight(1)
      Button($r('app.string.management_devices_title'), { type: ButtonType.Normal })
        .height(32)
        .padding({ left: 12, right: 12 })
        .borderRadius(this.nativeMaterial ? 16 : 8)
        .fontSize(13)
        .fontColor($r('app.color.color_main'))
        .backgroundColor(Color.Transparent)
        .attributeModifier(AppThemeSurfaceResolver.modifier(
          AppThemeMaterialRole.AdaptiveControl, this.nativeMaterial))
        .onClick(() => this.onOpenDevices())
    }
    .width('100%')
  }
}

@ComponentV2
struct ManagementSessionVisual {
  @Require @Param session: ManagementSessionItem
  @Require @Param themeStyle: ThemeStyle

  build() {
    Stack() {
      if (this.session.hasMedia && this.session.imageUrl) {
        ImageKnifeComponent({
          imageKnifeOption: {
            loadSrc: this.session.imageUrl,
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
          .accessibilityText(this.session.itemName)
      } else {
        SymbolGlyph(this.session.hasMedia ?
          (this.session.paused ? $r('sys.symbol.pause_fill') : $r('sys.symbol.play_fill')) :
          $r('sys.symbol.person_fill'))
          .fontSize(18)
          .fontColor([$r('app.color.color_main')])
          .width(42)
          .height(42)
          .backgroundColor(AppThemeSurfaceResolver.iconWellBackground(this.themeStyle))
          .borderRadius(12)
      }
    }
  }
}

@ComponentV2
struct ManagementSessionRow {
  @Require @Param session: ManagementSessionItem
  @Require @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false
  @Event onOpenSession: () => void = () => {}

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

  private deviceLine(): string {
    const deviceName = this.session.deviceName || this.getString('management_detail_unknown')
    return `${this.getString('management_detail_device')}: ${deviceName}`
  }

  private clientLine(): string {
    const client = `${this.session.client} ${this.session.applicationVersion}`.trim()
    if (this.session.userName && client) {
      return `${this.session.userName} · ${client}`
    }
    return this.session.userName || client
  }

  private idleActivityLine(): string {
    const formatted = ManagementSessionPresentation.formatActivityDate(this.session.lastActivityDate)
    const value = formatted || this.getString('management_detail_unknown')
    return `${this.getString('management_detail_last_activity')}: ${value}`
  }

  private formatTicks(ticks: number): string {
    const seconds = Math.max(0, Math.floor(ticks / 10000000))
    const minutes = Math.floor(seconds / 60)
    const remainder = seconds % 60
    return `${minutes}:${remainder.toString().padStart(2, '0')}`
  }

  private statusText(): ResourceStr {
    if (!this.session.hasMedia) return $r('app.string.management_session_idle')
    if (this.session.playbackKind === ManagementPlaybackKind.Transcode) {
      return $r('app.string.management_session_transcoding')
    }
    return this.session.paused ? $r('app.string.management_session_paused') :
      $r('app.string.management_session_playing')
  }

  build() {
    Column({ space: 6 }) {
      Row({ space: 10 }) {
        ManagementSessionVisual({
          session: this.session,
          themeStyle: this.themeStyle
        })
        Column({ space: 3 }) {
          Text(this.session.hasMedia ? this.session.itemName :
            (this.session.userName || this.session.deviceName || this.getString('management_detail_unknown')))
            .fontSize(14)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (this.session.hasMedia && this.session.itemSubtitle) {
            Text(this.session.itemSubtitle)
              .fontSize(12)
              .fontColor(this.secondaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          Text(this.deviceLine())
            .fontSize(12)
            .fontColor(this.secondaryTextColor())
            .maxLines(1)
            .textOverflow({ overflow: TextOverflow.Ellipsis })
          if (this.clientLine()) {
            Text(this.clientLine())
              .fontSize(11)
              .fontColor(this.secondaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
          if (!this.session.hasMedia) {
            Text(this.idleActivityLine())
              .fontSize(11)
              .fontColor(this.secondaryTextColor())
              .maxLines(1)
              .textOverflow({ overflow: TextOverflow.Ellipsis })
          }
        }
        .layoutWeight(1)
        .alignItems(HorizontalAlign.Start)
        Text(this.statusText())
          .fontSize(11)
          .fontColor(this.session.paused ? this.secondaryTextColor() : $r('app.color.color_main'))
      }
      if (this.session.hasMedia && this.session.runtimeTicks > 0) {
        Row({ space: 8 }) {
          Progress({ value: this.session.positionTicks, total: this.session.runtimeTicks, type: ProgressType.Linear })
            .layoutWeight(1)
            .height(4)
            .color($r('app.color.color_main'))
          Text(`${this.formatTicks(this.session.positionTicks)} / ${this.formatTicks(this.session.runtimeTicks)}`)
            .fontSize(10)
            .fontColor(this.secondaryTextColor())
        }
      }
      if (this.session.playbackKind === ManagementPlaybackKind.Transcode) {
        Progress({ value: this.session.transcodingPercent, total: 100, type: ProgressType.Linear })
          .width('100%')
          .height(3)
          .color($r('app.color.status_warning'))
      }
    }
    .width('100%')
    .padding({ top: 8, bottom: 8 })
    .onClick(() => this.onOpenSession())
  }
}

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

  build() {
    Column({ space: 8 }) {
      ManagementSessionsHeader({
        primaryTextColor: this.primaryTextColor(),
        nativeMaterial: this.useNativeMaterial(),
        onOpenDevices: () => this.onOpenDevices()
      })
      if ((this.ui.status === ManagementSectionStatus.Content ||
        this.ui.status === ManagementSectionStatus.Error) && this.ui.sessions.length > 0) {
        ForEach(this.ui.sessions, (session: ManagementSessionItem) => {
          ManagementSessionRow({
            session,
            themeStyle: this.themeStyle,
            nativeThemeAvailable: this.nativeThemeAvailable,
            onOpenSession: () => this.onOpenSession(session)
          })
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
          .fontColor(AppThemeSurfaceResolver.useNativeSurface(this.themeStyle, this.nativeThemeAvailable) ?
            $r('sys.color.font_secondary') : $r('app.color.text_1'))
      }
    }
    .width('100%')
  }
}
