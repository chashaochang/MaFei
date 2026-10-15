import {
  ManagementMediaDeleteSummary,
  ManagementMediaDeleteTarget
} from './ManagementLibraryModels'

export class ManagementMediaDeletePolicy {
  static canDelete(administrator: boolean, itemCanDelete: boolean, itemId: string): boolean {
    return administrator && itemCanDelete && itemId.trim().length > 0
  }

  static canDeleteTarget(administrator: boolean, target: ManagementMediaDeleteTarget): boolean {
    return ManagementMediaDeletePolicy.canDelete(administrator, target.canDelete, target.id) &&
      target.path.trim().length > 0
  }

  static summarize(targets: ManagementMediaDeleteTarget[], administrator: boolean): ManagementMediaDeleteSummary {
    const summary = new ManagementMediaDeleteSummary()
    targets.forEach((target: ManagementMediaDeleteTarget) => {
      if (ManagementMediaDeletePolicy.canDeleteTarget(administrator, target)) {
        if (summary.mediaName.length === 0 && target.name.trim().length > 0) {
          summary.mediaName = target.name
        }
        summary.allowedIds.push(target.id)
        summary.allowedNames.push(target.name.trim())
        summary.allowedPaths.push(target.path.trim())
      } else {
        summary.blockedNames.push(target.name)
      }
    })
    return summary
  }
}
