import {
  MANAGEMENT_ACTIVITY_PAGE_SIZE,
  ManagementActivityFilterOptions,
  ManagementActivityItem,
  ManagementActivitySortField,
  ManagementActivityView
} from './ManagementActivityModels';

export class ManagementActivityQuery {
  static hasUserId(view: ManagementActivityView): boolean | undefined {
    if (view === ManagementActivityView.User) return true
    if (view === ManagementActivityView.System) return false
    return undefined
  }

  static startIndex(pageIndex: number): number {
    return Math.max(0, pageIndex) * MANAGEMENT_ACTIVITY_PAGE_SIZE
  }

  static pageCount(totalRecordCount: number, pageSize: number = MANAGEMENT_ACTIVITY_PAGE_SIZE): number {
    const normalizedPageSize = Math.max(1, pageSize)
    return totalRecordCount <= 0 ? 0 : Math.ceil(totalRecordCount / normalizedPageSize)
  }

  static applyCurrentPage(items: ManagementActivityItem[], options: ManagementActivityFilterOptions):
    ManagementActivityItem[] {
    const query = options.text.trim().toLowerCase()
    const severities = options.severities.map((value: string) => value.toLowerCase())
    const users = options.userNames.map((value: string) => value.toLowerCase())
    return items.filter((item: ManagementActivityItem) => {
      const timestamp = Date.parse(item.date || '')
      const matchesText = !query || `${item.name} ${item.shortOverview} ${item.overview} ${item.type}`
        .toLowerCase().includes(query)
      const matchesFrom = options.fromMs <= 0 || (Number.isFinite(timestamp) && timestamp >= options.fromMs)
      const matchesTo = options.toMs <= 0 || (Number.isFinite(timestamp) && timestamp <= options.toMs)
      const matchesSeverity = severities.length === 0 || severities.includes(item.severity.toLowerCase())
      const matchesUser = users.length === 0 || users.includes(item.userName.toLowerCase())
      return matchesText && matchesFrom && matchesTo && matchesSeverity && matchesUser
    }).sort((left: ManagementActivityItem, right: ManagementActivityItem) =>
      ManagementActivityQuery.compare(left, right, options.sortField, options.ascending))
  }

  private static compare(left: ManagementActivityItem, right: ManagementActivityItem,
    field: ManagementActivitySortField, ascending: boolean): number {
    let first = left.date
    let second = right.date
    if (field === ManagementActivitySortField.Severity) {
      first = left.severity
      second = right.severity
    } else if (field === ManagementActivitySortField.User) {
      first = left.userName
      second = right.userName
    } else if (field === ManagementActivitySortField.Name) {
      first = left.name
      second = right.name
    } else if (field === ManagementActivitySortField.Type) {
      first = left.type
      second = right.type
    }
    const value = first.localeCompare(second)
    return ascending ? value : -value
  }
}
