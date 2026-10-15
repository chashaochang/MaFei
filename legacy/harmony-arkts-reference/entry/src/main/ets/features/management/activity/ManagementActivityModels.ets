import { ManagementErrorCode, ManagementPermissionState, ManagementSectionStatus } from '../ManagementModels';

export const MANAGEMENT_ACTIVITY_PAGE_SIZE: number = 25

export enum ManagementActivityView {
  All,
  User,
  System
}

export enum ManagementActivitySortField {
  Date,
  Severity,
  User,
  Name,
  Type
}

export class ManagementActivityItem {
  id: number = 0
  date: string = ''
  severity: string = ''
  userId: string = ''
  userName: string = ''
  name: string = ''
  shortOverview: string = ''
  overview: string = ''
  type: string = ''
  itemId: string = ''
}

export class ManagementActivityRequest {
  startIndex: number = 0
  limit: number = MANAGEMENT_ACTIVITY_PAGE_SIZE
  minDate: string = ''
  hasUserId: boolean | undefined = undefined
}

export class ManagementActivityFilterOptions {
  text: string = ''
  fromMs: number = 0
  toMs: number = 0
  severities: string[] = []
  userNames: string[] = []
  sortField: ManagementActivitySortField = ManagementActivitySortField.Date
  ascending: boolean = false
}

export class ManagementActivityPageResult {
  items: ManagementActivityItem[] = []
  totalRecordCount: number = 0
  startIndex: number = 0
}

export class ManagementActivityMediaTarget {
  available: boolean = false
  id: string = ''
  mediaName: string = ''
  isTvShows: boolean = false
}

export class ManagementActivityViewState {
  permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  status: ManagementSectionStatus = ManagementSectionStatus.Loading
  errorCode: ManagementErrorCode = ManagementErrorCode.None
}
