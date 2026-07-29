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
  Sessions,
  Tasks,
  Activities
}

export enum ManagementActivitySeverity {
  Information,
  Warning,
  Error
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

export class ManagementSessionItem {
  id: string = ''
  userName: string = ''
  client: string = ''
  deviceName: string = ''
  deviceType: string = ''
  applicationVersion: string = ''
  remoteEndPoint: string = ''
  itemName: string = ''
  paused: boolean = false
  muted: boolean = false
  canSeek: boolean = false
  volumeLevel: number = -1
  audioStreamIndex: number = -1
  subtitleStreamIndex: number = -1
  playMethod: string = ''
  mediaSourceId: string = ''
  supportsMediaControl: boolean = false
  supportsRemoteControl: boolean = false
  positionTicks: number = 0
  lastActivityDate: string = ''
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

export class ManagementActivityItem {
  id: string = ''
  title: string = ''
  overview: string = ''
  date: string = ''
  severity: ManagementActivitySeverity = ManagementActivitySeverity.Information
}

export class ManagementSectionResult {
  kind: ManagementSectionKind = ManagementSectionKind.Server
  status: ManagementSectionStatus = ManagementSectionStatus.Loading
  errorCode: ManagementErrorCode = ManagementErrorCode.None
  message: string = ''
  server: ManagementServerOverview | null = null
  counts: ManagementMediaCounts | null = null
  users: ManagementUserSummary | null = null
  sessions: ManagementSessionItem[] = []
  tasks: ManagementTaskItem[] = []
  activities: ManagementActivityItem[] = []
}

export class ManagementDashboardSnapshot {
  serverResult: ManagementSectionResult = new ManagementSectionResult()
  countsResult: ManagementSectionResult = new ManagementSectionResult()
  usersResult: ManagementSectionResult = new ManagementSectionResult()
  sessionsResult: ManagementSectionResult = new ManagementSectionResult()
  tasksResult: ManagementSectionResult = new ManagementSectionResult()
  activitiesResult: ManagementSectionResult = new ManagementSectionResult()
}

export class ManagementActionResult {
  ok: boolean = false
  code: ManagementActionCode = ManagementActionCode.None
  message: string = ''
}
