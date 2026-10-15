export enum ManagementPermissionState {
  Checking,
  Allowed,
  Denied,
  Error
}

export enum ManagementSectionStatus {
  Loading,
  Content,
  Empty,
  Error,
  Unsupported
}

export enum ManagementSectionKind {
  Server,
  Counts,
  Users,
  Tasks
}

export enum ManagementErrorCode {
  None,
  Unsupported,
  AuthenticationExpired,
  PermissionDenied,
  Offline,
  RequestFailed
}

export enum ManagementActionCode {
  None,
  ScanTaskUnavailable,
  ScanAlreadyRunning,
  ScanStarted,
  AuthenticationExpired,
  PermissionDenied,
  ScanStartFailed
}

export enum ManagementTaskActionCode {
  None,
  Started,
  Cancelled,
  Busy,
  AuthenticationExpired,
  PermissionDenied,
  Failed
}

export class ManagementServerOverview {
  name: string = ''
  version: string = ''
  productName: string = ''
  operatingSystem: string = ''
  address: string = ''
  online: boolean = false
  pendingRestart: boolean = false
}

export class ManagementMediaCounts {
  movies: number = 0
  series: number = 0
  episodes: number = 0
  total: number = 0
}

export class ManagementUserSummary {
  total: number = 0
  administrators: number = 0
  disabled: number = 0
}

export class ManagementTaskItem {
  id: string = ''
  key: string = ''
  name: string = ''
  description: string = ''
  category: string = ''
  state: string = ''
  running: boolean = false
  busy: boolean = false
  progress: number = 0
  lastExecutionStart: string = ''
  lastExecutionEnd: string = ''
  lastExecutionStatus: string = ''
  lastExecutionError: string = ''
}

export class ManagementSectionResult {
  kind: ManagementSectionKind = ManagementSectionKind.Server
  status: ManagementSectionStatus = ManagementSectionStatus.Loading
  errorCode: ManagementErrorCode = ManagementErrorCode.None
  message: string = ''
  server: ManagementServerOverview | null = null
  counts: ManagementMediaCounts | null = null
  users: ManagementUserSummary | null = null
  tasks: ManagementTaskItem[] = []
}

export class ManagementActionResult {
  ok: boolean = false
  code: ManagementActionCode = ManagementActionCode.None
  message: string = ''
}

export class ManagementTaskActionResult {
  ok: boolean = false
  code: ManagementTaskActionCode = ManagementTaskActionCode.None
  message: string = ''
}
