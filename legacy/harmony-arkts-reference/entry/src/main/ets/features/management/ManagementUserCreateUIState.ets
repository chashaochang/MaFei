import { BaseUIState } from '../../common/base/BaseUIState';
import { ManagementPermissionState } from './ManagementModels';
import {
  ManagementAccessDraft,
  ManagementUserOperationStatus,
  ManagementUserOption
} from './ManagementUserModels';

@ObservedV2
export class ManagementUserCreateUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace permissionMessage: string = ''
  @Trace name: string = ''
  @Trace password: string = ''
  @Trace confirmPassword: string = ''
  @Trace access: ManagementAccessDraft = new ManagementAccessDraft()
  @Trace saving: boolean = false
  @Trace createdUserId: string = ''
  @Trace partialSuccess: boolean = false
  @Trace folders: ManagementUserOption[] = []
  @Trace foldersStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace channels: ManagementUserOption[] = []
  @Trace channelsStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace message: string = ''
}
