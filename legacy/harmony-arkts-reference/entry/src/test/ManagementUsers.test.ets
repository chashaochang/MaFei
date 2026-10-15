import { describe, expect, it } from '@ohos/hypium'
import { ManagementUserQuery, ManagementUserQueryOptions } from
  '../main/ets/features/management/ManagementUserQuery'
import {
  ManagementUserItem,
  ManagementUserRoleFilter,
  ManagementUserSortField,
  ManagementUserStatusFilter,
  ManagementUserVisibilityFilter
} from '../main/ets/features/management/ManagementUserModels'

function user(id: string, name: string, administrator: boolean, disabled: boolean,
  hidden: boolean, lastActivityDate: string): ManagementUserItem {
  const item = new ManagementUserItem()
  item.id = id
  item.name = name
  item.administrator = administrator
  item.disabled = disabled
  item.hidden = hidden
  item.lastActivityDate = lastActivityDate
  return item
}

function users(): ManagementUserItem[] {
  return [
    user('enabled-visible-admin', 'Admin', true, false, false, '2026-08-01T10:00:00Z'),
    user('disabled-admin', 'Disabled Admin', true, true, false, '2026-08-02T10:00:00Z'),
    user('hidden-user', 'Hidden', false, false, true, '2026-08-03T10:00:00Z')
  ]
}

export default function managementUsersTest() {
  describe('ManagementUsers', () => {
    it('combinesRoleStatusAndVisibility', 0, () => {
      const options = new ManagementUserQueryOptions()
      options.role = ManagementUserRoleFilter.Administrators
      options.status = ManagementUserStatusFilter.Enabled
      options.visibility = ManagementUserVisibilityFilter.Visible
      const result = ManagementUserQuery.apply(users(), options)
      expect(result.length).assertEqual(1)
      expect(result[0].id).assertEqual('enabled-visible-admin')
    })

    it('sortsLatestActivityFirst', 0, () => {
      const options = new ManagementUserQueryOptions()
      options.sortField = ManagementUserSortField.LastActivity
      options.ascending = false
      const result = ManagementUserQuery.apply(users(), options)
      expect(result[0].id).assertEqual('hidden-user')
    })

    it('searchesTrimmedCaseInsensitiveName', 0, () => {
      const options = new ManagementUserQueryOptions()
      options.query = '  DISABLED  '
      const result = ManagementUserQuery.apply(users(), options)
      expect(result.length).assertEqual(1)
      expect(result[0].id).assertEqual('disabled-admin')
    })
  })
}
