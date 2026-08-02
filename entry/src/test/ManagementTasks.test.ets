import { describe, expect, it } from '@ohos/hypium'
import {
  ManagementTaskActionCode,
  ManagementTaskItem
} from '../main/ets/features/management/ManagementModels'
import {
  ManagementTaskActionPolicy
} from '../main/ets/features/management/ManagementTaskActionPolicy'

function task(id: string, busy: boolean): ManagementTaskItem {
  const value = new ManagementTaskItem()
  value.id = id
  value.busy = busy
  return value
}

export default function managementTasksTest() {
  describe('ManagementTasks', () => {
    it('rejectsEmptyTaskId', 0, () => {
      expect(ManagementTaskActionPolicy.canStart(task('', false))).assertFalse()
      expect(ManagementTaskActionPolicy.canCancel(task('', true))).assertFalse()
    })

    it('rejectsStartWhileBusy', 0, () => {
      expect(ManagementTaskActionPolicy.canStart(task('busy', true))).assertFalse()
    })

    it('rejectsCancelWhileIdle', 0, () => {
      expect(ManagementTaskActionPolicy.canCancel(task('idle', false))).assertFalse()
    })

    it('mapsAuthenticationFailure', 0, () => {
      expect(ManagementTaskActionPolicy.codeForHttpStatus(401))
        .assertEqual(ManagementTaskActionCode.AuthenticationExpired)
    })

    it('mapsPermissionFailure', 0, () => {
      expect(ManagementTaskActionPolicy.codeForHttpStatus(403))
        .assertEqual(ManagementTaskActionCode.PermissionDenied)
    })
  })
}
