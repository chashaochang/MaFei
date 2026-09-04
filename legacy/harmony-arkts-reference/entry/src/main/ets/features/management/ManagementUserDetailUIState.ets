import { BaseUIState } from '../../common/base/BaseUIState';
import { ManagementPermissionState } from './ManagementModels';
import {
  ManagementAccessDraft,
  ManagementParentalDraft,
  ManagementParentalRatingOption,
  ManagementPasswordDraft,
  ManagementProfileDraft,
  ManagementUserItem,
  ManagementUserOperationStatus,
  ManagementUserOption,
  ManagementUserTab
} from './ManagementUserModels';

@ObservedV2
export class ManagementUserDetailUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace permissionMessage: string = ''
  @Trace userId: string = ''
  @Trace currentUserId: string = ''
  @Trace user: ManagementUserItem = new ManagementUserItem()
  @Trace userName: string = ''
  @Trace enabledAdministratorCount: number = 0
  @Trace selectedTab: ManagementUserTab = ManagementUserTab.Profile
  @Trace profile: ManagementProfileDraft = new ManagementProfileDraft()
  @Trace access: ManagementAccessDraft = new ManagementAccessDraft()
  @Trace parental: ManagementParentalDraft = new ManagementParentalDraft()
  @Trace password: ManagementPasswordDraft = new ManagementPasswordDraft()
  @Trace authenticationProviders: ManagementUserOption[] = []
  @Trace passwordResetProviders: ManagementUserOption[] = []
  @Trace folders: ManagementUserOption[] = []
  @Trace foldersStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace channels: ManagementUserOption[] = []
  @Trace channelsStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace devices: ManagementUserOption[] = []
  @Trace devicesStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace parentalRatings: ManagementParentalRatingOption[] = []
  @Trace profileDirty: boolean = false
  @Trace accessDirty: boolean = false
  @Trace parentalDirty: boolean = false
  @Trace passwordDirty: boolean = false
  @Trace profileStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace accessStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace parentalStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace passwordStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace deleteStatus: ManagementUserOperationStatus = ManagementUserOperationStatus.Idle
  @Trace message: string = ''

  get currentTabDirty(): boolean {
    if (this.selectedTab === ManagementUserTab.Access) {
      return this.accessDirty
    }
    if (this.selectedTab === ManagementUserTab.Parental) {
      return this.parentalDirty
    }
    if (this.selectedTab === ManagementUserTab.Password) {
      return this.passwordDirty
    }
    return this.profileDirty
  }

  get anyDirty(): boolean {
    return this.profileDirty || this.accessDirty || this.parentalDirty || this.passwordDirty
  }
}
