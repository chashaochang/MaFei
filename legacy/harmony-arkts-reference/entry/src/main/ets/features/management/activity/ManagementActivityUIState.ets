import { BaseUIState } from '../../../common/base/BaseUIState';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSectionStatus
} from '../ManagementModels';
import {
  ManagementActivityItem,
  ManagementActivitySortField,
  ManagementActivityView
} from './ManagementActivityModels';

@ObservedV2
export class ManagementActivityDashboardUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace status: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace errorCode: ManagementErrorCode = ManagementErrorCode.None
  @Trace message: string = ''
  @Trace items: ManagementActivityItem[] = []
  @Trace stale: boolean = false
  @Trace refreshing: boolean = false
}

@ObservedV2
export class ManagementActivityUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace status: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace errorCode: ManagementErrorCode = ManagementErrorCode.None
  @Trace message: string = ''
  @Trace view: ManagementActivityView = ManagementActivityView.All
  @Trace pageIndex: number = 0
  @Trace displayedPageIndex: number = 0
  @Trace retryPageIndex: number = 0
  @Trace pageCount: number = 0
  @Trace totalRecordCount: number = 0
  @Trace sourceItems: ManagementActivityItem[] = []
  @Trace items: ManagementActivityItem[] = []
  @Trace query: string = ''
  @Trace fromMs: number = 0
  @Trace toMs: number = 0
  @Trace selectedSeverities: string[] = []
  @Trace selectedUsers: string[] = []
  @Trace sortField: ManagementActivitySortField = ManagementActivitySortField.Date
  @Trace ascending: boolean = false
  @Trace stale: boolean = false
  @Trace refreshing: boolean = false
  @Trace pendingItemId: string = ''
}
