import {
  ManagementTaskActionCode,
  ManagementTaskItem
} from './ManagementModels'

export class ManagementTaskActionPolicy {
  static canStart(task: ManagementTaskItem): boolean {
    return task.id.length > 0 && !task.busy
  }

  static canCancel(task: ManagementTaskItem): boolean {
    return task.id.length > 0 && task.busy
  }

  static codeForHttpStatus(status: number): ManagementTaskActionCode {
    if (status === 401) return ManagementTaskActionCode.AuthenticationExpired
    if (status === 403) return ManagementTaskActionCode.PermissionDenied
    return ManagementTaskActionCode.Failed
  }
}
