import {
  ManagementUserItem,
  ManagementUserRoleFilter,
  ManagementUserSortField,
  ManagementUserStatusFilter,
  ManagementUserVisibilityFilter
} from './ManagementUserModels'

export class ManagementUserQueryOptions {
  query: string = ''
  role: ManagementUserRoleFilter = ManagementUserRoleFilter.All
  status: ManagementUserStatusFilter = ManagementUserStatusFilter.All
  visibility: ManagementUserVisibilityFilter = ManagementUserVisibilityFilter.All
  sortField: ManagementUserSortField = ManagementUserSortField.Name
  ascending: boolean = true

  static copyOf(source: ManagementUserQueryOptions): ManagementUserQueryOptions {
    const result = new ManagementUserQueryOptions()
    result.query = source.query
    result.role = source.role
    result.status = source.status
    result.visibility = source.visibility
    result.sortField = source.sortField
    result.ascending = source.ascending
    return result
  }
}

export class ManagementUserQuery {
  static apply(items: ManagementUserItem[], options: ManagementUserQueryOptions): ManagementUserItem[] {
    const query = options.query.trim().toLocaleLowerCase()
    const result = items.filter((item: ManagementUserItem): boolean => {
      return ManagementUserQuery.matchesQuery(item, query) &&
        ManagementUserQuery.matchesRole(item, options.role) &&
        ManagementUserQuery.matchesStatus(item, options.status) &&
        ManagementUserQuery.matchesVisibility(item, options.visibility)
    })
    result.sort((left: ManagementUserItem, right: ManagementUserItem): number => {
      const comparison = options.sortField === ManagementUserSortField.LastActivity ?
        ManagementUserQuery.activityTime(left) - ManagementUserQuery.activityTime(right) :
        left.name.toLocaleLowerCase().localeCompare(right.name.toLocaleLowerCase())
      return options.ascending ? comparison : -comparison
    })
    return result
  }

  private static matchesQuery(item: ManagementUserItem, query: string): boolean {
    return query.length === 0 || item.name.toLocaleLowerCase().includes(query)
  }

  private static matchesRole(item: ManagementUserItem, role: ManagementUserRoleFilter): boolean {
    if (role === ManagementUserRoleFilter.Administrators) {
      return item.administrator
    }
    if (role === ManagementUserRoleFilter.Standard) {
      return !item.administrator
    }
    return true
  }

  private static matchesStatus(item: ManagementUserItem, status: ManagementUserStatusFilter): boolean {
    if (status === ManagementUserStatusFilter.Enabled) {
      return !item.disabled
    }
    if (status === ManagementUserStatusFilter.Disabled) {
      return item.disabled
    }
    return true
  }

  private static matchesVisibility(item: ManagementUserItem,
    visibility: ManagementUserVisibilityFilter): boolean {
    if (visibility === ManagementUserVisibilityFilter.Visible) {
      return !item.hidden
    }
    if (visibility === ManagementUserVisibilityFilter.Hidden) {
      return item.hidden
    }
    return true
  }

  private static activityTime(item: ManagementUserItem): number {
    const time = Date.parse(item.lastActivityDate)
    return Number.isFinite(time) ? time : 0
  }
}
