import { ManagementErrorCode, ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';

export const MANAGEMENT_DEVICE_PAGE_SIZE: number = 25

export enum ManagementDeviceSortField {
  LastActivity,
  Name,
  App,
  User
}

export class ManagementDeviceItem {
  id: string = ''
  name: string = ''
  customName: string = ''
  displayName: string = ''
  appName: string = ''
  appVersion: string = ''
  lastUserId: string = ''
  lastUserName: string = ''
  lastActivityDate: string = ''
  current: boolean = false
}

export class ManagementDeviceQueryOptions {
  text: string = ''
  fromMs: number = 0
  toMs: number = 0
  userNames: string[] = []
  sortField: ManagementDeviceSortField = ManagementDeviceSortField.LastActivity
  ascending: boolean = false
  pageIndex: number = 0
  pageSize: number = MANAGEMENT_DEVICE_PAGE_SIZE
}

export class ManagementDevicePageResult {
  items: ManagementDeviceItem[] = []
  filteredItems: ManagementDeviceItem[] = []
  pageIndex: number = 0
  pageCount: number = 0
  totalCount: number = 0
}

export class ManagementDeviceActionResult {
  ok: boolean = false
  skipped: boolean = false
  errorCode: ManagementErrorCode = ManagementErrorCode.None
  message: string = ''
}

export class ManagementDeviceBatchResult {
  succeededIds: string[] = []
  failedIds: string[] = []
}

export class ManagementDeviceViewState {
  permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  status: ManagementSectionStatus = ManagementSectionStatus.Loading
  errorCode: ManagementErrorCode = ManagementErrorCode.None
}
