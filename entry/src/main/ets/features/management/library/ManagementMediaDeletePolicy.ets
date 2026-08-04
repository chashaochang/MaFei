import {
  ManagementMediaDeleteSummary,
  ManagementMediaDeleteTarget
} from './ManagementLibraryModels'

export class ManagementMediaDeletePolicy {
  static canDelete(administrator: boolean, itemCanDelete: boolean, itemId: string): boolean {
    return administrator && itemCanDelete && itemId.trim().length > 0
  }

  static summarize(targets: ManagementMediaDeleteTarget[], administrator: boolean): ManagementMediaDeleteSummary {
    const summary = new ManagementMediaDeleteSummary()
    targets.forEach((target: ManagementMediaDeleteTarget) => {
      if (ManagementMediaDeletePolicy.canDelete(administrator, target.canDelete, target.id)) {
        summary.allowedIds.push(target.id)
        if (target.path.trim().length > 0) {
          summary.allowedPaths.push(target.path)
        }
      } else {
        summary.blockedNames.push(target.name)
      }
    })
    return summary
  }
}
