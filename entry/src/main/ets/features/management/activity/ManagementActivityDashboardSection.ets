import { ThemeStyle } from '../../../entity/ThemeStyle';
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver';
import { ManagementSectionStatus } from '../ManagementModels';
import { ManagementActivityItem } from './ManagementActivityModels';
import { ManagementActivityDashboardUIState } from './ManagementActivityUIState';

@ComponentV2
export struct ManagementActivityDashboardSection {
  @Require
  @Param ui: ManagementActivityDashboardUIState
  @Require
  @Param themeStyle: ThemeStyle
  @Param nativeThemeAvailable: boolean = false

  @Event onRetry: () => void = () => {
  }

  @Event onOpenAll: () => void = () => {
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

  private severityColor(value: string): ResourceColor {
    if (value === 'Error' || value === 'Critical') return $r('app.color.main_red_color')
    if (value === 'Warning') return $r('app.color.status_warning')
    return $r('app.color.color_main')
  }

  private formatDate(value: string): string {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    const month = (date.getMonth() + 1).toString().padStart(2, '0')
    const day = date.getDate().toString().padStart(2, '0')
    const hour = date.getHours().toString().padStart(2, '0')
    const minute = date.getMinutes().toString().padStart(2, '0')
    return `${month}-${day} ${hour}:${minute}`
  }

  build() {
    Column({ space: 8 }) {
      Row() {
        Text($r('app.string.management_recent_activity'))
          .fontSize(18).fontWeight(FontWeight.Bold).fontColor(this.primaryTextColor())
          .layoutWeight(1)
        Button($r('app.string.management_activity_view_all'), { type: ButtonType.Normal })
          .height(32).borderRadius(this.useNativeMaterial() ? 16 : 8)
          .fontSize(13).fontColor($r('app.color.color_main'))
          .backgroundColor(Color.Transparent)
          .attributeModifier(AppThemeSurfaceResolver.modifier(
            AppThemeMaterialRole.AdaptiveControl, this.useNativeMaterial()))
          .onClick(() => this.onOpenAll())
      }
      .width('100%')
      if ((this.ui.status === ManagementSectionStatus.Content ||
        this.ui.status === ManagementSectionStatus.Error) && this.ui.items.length > 0) {
        ForEach(this.ui.items, (item: ManagementActivityItem) => {
          Row({ space: 10 }) {
            Row().width(8).height(8).borderRadius(4).backgroundColor(this.severityColor(item.severity))
            Column({ space: 3 }) {
              Text(item.name).fontSize(13).fontWeight(FontWeight.Medium)
                .fontColor(this.primaryTextColor()).maxLines(1)
                .textOverflow({ overflow: TextOverflow.Ellipsis })
              if (item.shortOverview) {
                Text(item.shortOverview).fontSize(12).fontColor(this.secondaryTextColor())
                  .maxLines(2).textOverflow({ overflow: TextOverflow.Ellipsis })
              }
            }
            .layoutWeight(1).alignItems(HorizontalAlign.Start)
            Text(this.formatDate(item.date)).fontSize(11).fontColor(this.secondaryTextColor())
          }
          .width('100%').padding({ top: 7, bottom: 7 })
        }, (item: ManagementActivityItem) => item.id.toString())
        if (this.ui.stale) {
          Text($r('app.string.management_stale_result')).fontSize(12)
            .fontColor($r('app.color.status_warning'))
        }
      } else if (this.ui.status === ManagementSectionStatus.Error) {
        Column({ space: 10 }) {
          Text($r('app.string.management_activity_failed'))
            .fontSize(13).fontColor(this.secondaryTextColor())
          if (this.useNativeMaterial()) {
            Button($r('app.string.management_retry'), { type: ButtonType.Normal })
              .height(34)
              .borderRadius(17)
              .backgroundColor(Color.Transparent)
              .attributeModifier(AppThemeSurfaceResolver.modifier(
                AppThemeMaterialRole.AdaptiveControl, true))
              .enabled(!this.ui.refreshing)
              .onClick(() => this.onRetry())
          } else {
            Button($r('app.string.management_retry'), { type: ButtonType.Normal })
              .height(34)
              .borderRadius(8)
              .attributeModifier(AppThemeSurfaceResolver.modifier(
                AppThemeMaterialRole.AdaptiveControl, false))
              .enabled(!this.ui.refreshing)
              .onClick(() => this.onRetry())
          }
        }
        .width('100%').alignItems(HorizontalAlign.Start)
      } else if (this.ui.status === ManagementSectionStatus.Loading) {
        LoadingProgress().width(24).height(24).color($r('app.color.color_main'))
      } else {
        Text(this.ui.status === ManagementSectionStatus.Unsupported ?
          $r('app.string.management_section_unsupported') : $r('app.string.management_activity_empty'))
          .fontSize(13).fontColor(this.secondaryTextColor())
      }
    }
    .width('100%')
  }
}
