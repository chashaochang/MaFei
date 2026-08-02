import { BaseUIState } from '../../../common/base/BaseUIState';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSectionStatus
} from '../ManagementModels';
import {
  ManagementDeviceBatchResult,
  ManagementDeviceItem,
  ManagementDeviceSortField
} from './ManagementDeviceModels';

@ObservedV2
export class ManagementDevicesUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace status: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace errorCode: ManagementErrorCode = ManagementErrorCode.None
  @Trace message: string = ''
  @Trace sourceDevices: ManagementDeviceItem[] = []
  @Trace devices: ManagementDeviceItem[] = []
  @Trace selectedDeviceId: string = ''
  @Trace query: string = ''
  @Trace recentDays: number = 0
  @Trace fromMs: number = 0
  @Trace toMs: number = 0
  @Trace selectedUsers: string[] = []
  @Trace sortField: ManagementDeviceSortField = ManagementDeviceSortField.LastActivity
  @Trace ascending: boolean = false
  @Trace pageIndex: number = 0
  @Trace pageCount: number = 0
  @Trace totalCount: number = 0
  @Trace stale: boolean = false
  @Trace refreshing: boolean = false
  @Trace editingDeviceId: string = ''
  @Trace renameDraft: string = ''
  @Trace pendingDeviceId: string = ''
  @Trace batchPending: boolean = false
  @Trace batchResult: ManagementDeviceBatchResult = new ManagementDeviceBatchResult()
  @Trace filterSheetVisible: boolean = false
}
