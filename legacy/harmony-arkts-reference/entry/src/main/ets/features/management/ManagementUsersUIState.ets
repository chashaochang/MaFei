import { BaseUIState } from '../../common/base/BaseUIState';
import { ManagementPermissionState, ManagementSectionStatus } from './ManagementModels';
import { ManagementUserQuery, ManagementUserQueryOptions } from './ManagementUserQuery';
import {
  ManagementUserItem,
  ManagementUserRoleFilter,
  ManagementUserSortField,
  ManagementUserStatusFilter,
  ManagementUserVisibilityFilter
} from './ManagementUserModels';

@ObservedV2
export class ManagementUsersUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace permissionMessage: string = ''
  @Trace status: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace users: ManagementUserItem[] = []
  @Trace query: string = ''
  @Trace roleFilter: ManagementUserRoleFilter = ManagementUserRoleFilter.All
  @Trace statusFilter: ManagementUserStatusFilter = ManagementUserStatusFilter.All
  @Trace visibilityFilter: ManagementUserVisibilityFilter = ManagementUserVisibilityFilter.All
  @Trace sortField: ManagementUserSortField = ManagementUserSortField.Name
  @Trace ascending: boolean = true
  @Trace filterSheetVisible: boolean = false
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
    const options = new ManagementUserQueryOptions()
    options.query = this.query
    options.role = this.roleFilter
    options.status = this.statusFilter
    options.visibility = this.visibilityFilter
    options.sortField = this.sortField
    options.ascending = this.ascending
    return ManagementUserQuery.apply(this.users, options)
  }

  get activeAdvancedFilterCount(): number {
    return (this.statusFilter === ManagementUserStatusFilter.All ? 0 : 1) +
      (this.visibilityFilter === ManagementUserVisibilityFilter.All ? 0 : 1) +
      (this.sortField === ManagementUserSortField.Name && this.ascending ? 0 : 1)
  }
}
