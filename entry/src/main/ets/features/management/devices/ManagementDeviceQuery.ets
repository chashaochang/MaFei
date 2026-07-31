import {
  ManagementDeviceItem,
  ManagementDevicePageResult,
  ManagementDeviceQueryOptions,
  ManagementDeviceSortField
} from './ManagementDeviceModels';

export class ManagementDeviceQuery {
  static apply(devices: ManagementDeviceItem[], options: ManagementDeviceQueryOptions):
    ManagementDevicePageResult {
    const query = options.text.trim().toLowerCase()
    const users = options.userNames.map((value: string) => value.toLowerCase())
    const filtered = devices.filter((item: ManagementDeviceItem) => {
      const matchesText = !query || `${item.displayName} ${item.appName} ${item.lastUserName}`
        .toLowerCase().includes(query)
      const activeAt = Date.parse(item.lastActivityDate || '')
      const matchesFrom = options.fromMs <= 0 || (Number.isFinite(activeAt) && activeAt >= options.fromMs)
      const matchesTo = options.toMs <= 0 || (Number.isFinite(activeAt) && activeAt <= options.toMs)
      const matchesUser = users.length === 0 || users.includes(item.lastUserName.toLowerCase())
      return matchesText && matchesFrom && matchesTo && matchesUser
    }).sort((left: ManagementDeviceItem, right: ManagementDeviceItem) =>
      ManagementDeviceQuery.compare(left, right, options.sortField, options.ascending))
    const pageSize = Math.max(1, options.pageSize)
    const pageCount = filtered.length === 0 ? 0 : Math.ceil(filtered.length / pageSize)
    const pageIndex = pageCount === 0 ? 0 : Math.max(0, Math.min(options.pageIndex, pageCount - 1))
    const result = new ManagementDevicePageResult()
    result.filteredItems = filtered
    result.pageIndex = pageIndex
    result.pageCount = pageCount
    result.totalCount = filtered.length
    result.items = filtered.slice(pageIndex * pageSize, pageIndex * pageSize + pageSize)
    return result
  }

  private static compare(left: ManagementDeviceItem, right: ManagementDeviceItem,
    field: ManagementDeviceSortField, ascending: boolean): number {
    let first = ''
    let second = ''
    if (field === ManagementDeviceSortField.Name) {
      first = left.displayName
      second = right.displayName
    } else if (field === ManagementDeviceSortField.App) {
      first = `${left.appName} ${left.appVersion}`
      second = `${right.appName} ${right.appVersion}`
    } else if (field === ManagementDeviceSortField.User) {
      first = left.lastUserName
      second = right.lastUserName
    } else {
      first = left.lastActivityDate
      second = right.lastActivityDate
    }
    const value = first.localeCompare(second)
    return ascending ? value : -value
  }
}
