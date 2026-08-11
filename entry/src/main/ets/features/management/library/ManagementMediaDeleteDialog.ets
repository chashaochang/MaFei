import { deviceInfo } from '@kit.BasicServicesKit'
import promptAction from '@ohos.promptAction'
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from '../../../theme/AppThemeSurfaceResolver'
import { ManagementMediaDeleteSummary } from './ManagementLibraryModels'

export class ManagementMediaDeleteDialog {
  static show(context: UIContext, summary: ManagementMediaDeleteSummary,
    onConfirm: () => void, onCancel: () => void = () => {}): void {
    const options: promptAction.ShowDialogOptions = {
      title: $r('app.string.management_media_delete_title'),
      message: ManagementMediaDeleteDialog.message(context, summary),
      buttons: [
        { text: $r('app.string.management_cancel'), color: $r('app.color.text_1') },
        { text: $r('app.string.management_media_delete'), color: $r('app.color.main_red_color') }
      ],
      backgroundColor: deviceInfo.sdkApiVersion >= 26 ? Color.Transparent : undefined,
      backgroundBlurStyle: deviceInfo.sdkApiVersion >= 26 ? BlurStyle.NONE : undefined,
      systemMaterial: deviceInfo.sdkApiVersion >= 26 ?
        AppThemeSurfaceResolver.material(AppThemeMaterialRole.Floating) : undefined
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
      lines.push(`${ManagementMediaDeleteDialog.getString(context, 'management_media_delete')} ` +
        `(${summary.allowedIds.length})`)
    } else if (summary.mediaName.length > 0) {
      lines.push(summary.mediaName)
    }
    lines.push(ManagementMediaDeleteDialog.getString(context, 'management_media_delete_message'))
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
        lines.push('...')
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
