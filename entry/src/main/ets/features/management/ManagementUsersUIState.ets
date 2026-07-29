import { BaseUIState } from '../../common/base/BaseUIState';
import { ManagementPermissionState, ManagementSectionStatus } from './ManagementModels';
import { ManagementUserFilter, ManagementUserItem } from './ManagementUserModels';

@ObservedV2
export class ManagementUsersUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace permissionMessage: string = ''
  @Trace status: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace users: ManagementUserItem[] = []
  @Trace query: string = ''
  @Trace filter: ManagementUserFilter = ManagementUserFilter.All
  @Trace isRefreshing: boolean = false
  @Trace refreshStatus: RefreshStatus = RefreshStatus.Inactive
  @Trace refreshOffset: number = 0
  @Trace hasStaleData: boolean = false
  @Trace selectedUserId: string = ''
  @Trace currentUserId: string = ''
  @Trace enabledAdministratorCount: number = 0
  @Trace deletingUserId: string = ''
  @Trace message: string = ''

  get filteredUsers(): ManagementUserItem[] {
    const query = this.query.trim().toLocaleLowerCase()
    return this.users.filter((user: ManagementUserItem) =>
      (query.length === 0 || user.name.toLocaleLowerCase().includes(query)) &&
        this.matchesFilter(user))
  }

  private matchesFilter(user: ManagementUserItem): boolean {
    if (this.filter === ManagementUserFilter.Administrators) {
      return user.administrator
    }
    if (this.filter === ManagementUserFilter.Standard) {
      return !user.administrator
    }
    if (this.filter === ManagementUserFilter.Disabled) {
      return user.disabled
    }
    if (this.filter === ManagementUserFilter.Hidden) {
      return user.hidden
    }
    return true
  }
}
