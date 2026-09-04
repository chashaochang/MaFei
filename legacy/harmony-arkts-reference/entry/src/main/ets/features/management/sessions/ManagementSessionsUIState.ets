import { BaseUIState } from '../../../common/base/BaseUIState';
import {
  ManagementErrorCode,
  ManagementPermissionState,
  ManagementSectionStatus
} from '../ManagementModels';
import { ManagementSessionAction, ManagementSessionItem } from './ManagementSessionModels';

@ObservedV2
export class ManagementSessionsUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace status: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace errorCode: ManagementErrorCode = ManagementErrorCode.None
  @Trace message: string = ''
  @Trace sessions: ManagementSessionItem[] = []
  @Trace detail: ManagementSessionItem | null = null
  @Trace stale: boolean = false
  @Trace ended: boolean = false
  @Trace refreshing: boolean = false
  @Trace pendingAction: ManagementSessionAction = ManagementSessionAction.None
  @Trace messageDraft: string = ''
}
