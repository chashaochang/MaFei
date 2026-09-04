import promptAction from '@ohos.promptAction'
import { ThemeStyle } from '../../../entity/ThemeStyle'
import {
  AppThemeOverlayPolicy,
  OverlayMaterialDecision,
  OverlaySurfaceRole
} from '../../../theme/AppThemeOverlayPolicy'
import { AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { HdsUiCapability } from '../../../theme/HdsUiCapability'
import { ManagementMediaDeleteSummary } from './ManagementLibraryModels'

interface ManagementMediaDeleteDialogOptions extends promptAction.ShowDialogOptions {
  systemMaterial?: Object
}

export class ManagementMediaDeleteDialog {
  private static materialDecision(): OverlayMaterialDecision {
    return AppThemeOverlayPolicy.resolve(
      OverlaySurfaceRole.Dangerous,
      ThemeStyle.Feiniu,
      HdsUiCapability.supportsSystemMaterial(),
      false
    )
  }

  static show(context: UIContext, summary: ManagementMediaDeleteSummary,
    onConfirm: () => void, onCancel: () => void = () => {}): void {
    let options: ManagementMediaDeleteDialogOptions
    if (ManagementMediaDeleteDialog.materialDecision() === OverlayMaterialDecision.DisableSystemMaterial) {
      options = {
        title: $r('app.string.management_media_delete_title'),
        message: ManagementMediaDeleteDialog.message(context, summary),
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_media_delete'), color: $r('app.color.main_red_color') }
        ],
        systemMaterial: AppThemeSurfaceResolver.disabledSystemMaterial()
      }
    } else {
      options = {
        title: $r('app.string.management_media_delete_title'),
        message: ManagementMediaDeleteDialog.message(context, summary),
        buttons: [
          { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
          { text: $r('app.string.management_media_delete'), color: $r('app.color.main_red_color') }
        ]
      }
    }
    context.getPromptAction().showDialog(options).then((response) => {
      if (response.index === 1) {
        onConfirm()
      } else {
        onCancel()
      }
    }).catch(() => {
      onCancel()
    })
  }

  private static message(context: UIContext, summary: ManagementMediaDeleteSummary): string {
    const lines: string[] = []
    const isBatch = summary.allowedIds.length > 1
    if (isBatch) {
      lines.push(context.getHostContext()?.resourceManager.getStringSync(
        $r('app.string.management_media_delete_count', summary.allowedIds.length)) ||
        `${summary.allowedIds.length}`)
    } else if (summary.mediaName.length > 0) {
      lines.push(summary.mediaName)
    }
    if (!isBatch) {
      lines.push(ManagementMediaDeleteDialog.getString(context, 'management_media_delete_message'))
    }
    if (isBatch) {
      const visibleCount = Math.min(summary.allowedIds.length, 5)
      for (let index = 0; index < visibleCount; index++) {
        const name = summary.allowedNames[index] || summary.allowedIds[index]
        lines.push(`• ${name}`)
        if (summary.allowedPaths[index]) {
          lines.push(`  ${summary.allowedPaths[index]}`)
        }
      }
      if (summary.allowedIds.length > visibleCount) {
        lines.push(context.getHostContext()?.resourceManager.getStringSync(
          $r('app.string.management_media_delete_more', summary.allowedIds.length - visibleCount)) ||
          `${summary.allowedIds.length - visibleCount}`)
      }
    } else {
      lines.push(ManagementMediaDeleteDialog.getString(context, 'management_media_delete_path'))
      if (summary.allowedPaths.length > 0) {
        summary.allowedPaths.forEach((path: string) => lines.push(`• ${path}`))
      }
    }
    lines.push('')
    lines.push(ManagementMediaDeleteDialog.getString(context, 'management_media_delete_notice'))
    if (summary.blockedNames.length > 0) {
      lines.push('')
      lines.push(ManagementMediaDeleteDialog.getString(context, 'management_media_delete_blocked'))
      summary.blockedNames.forEach((name: string) => lines.push(`• ${name}`))
    }
    return lines.join('\n')
  }

  private static getString(context: UIContext, name: string): string {
    return context.getHostContext()?.resourceManager.getStringByNameSync(name) || name
  }
}
