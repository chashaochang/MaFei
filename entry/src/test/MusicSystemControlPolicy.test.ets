import { describe, expect, it } from '@ohos/hypium'
import {
  BackgroundTaskOwner,
  BackgroundTaskOwnerPolicy
} from '../main/ets/utils/BackgroundTaskManager'

export default function musicSystemControlPolicyTest() {
  describe('MusicSystemControlPolicy', () => {
    it('doesNotStopWhileAnotherOwnerIsActive', 0, () => {
      const policy = new BackgroundTaskOwnerPolicy()

      expect(policy.acquire(BackgroundTaskOwner.Video)).assertTrue()
      expect(policy.acquire(BackgroundTaskOwner.Music)).assertFalse()
      expect(policy.release(BackgroundTaskOwner.Video)).assertFalse()
      expect(policy.release(BackgroundTaskOwner.Music)).assertTrue()
    })

    it('keepsOwnerAcquisitionAndReleaseIdempotent', 0, () => {
      const policy = new BackgroundTaskOwnerPolicy()

      expect(policy.acquire(BackgroundTaskOwner.Music)).assertTrue()
      expect(policy.acquire(BackgroundTaskOwner.Music)).assertFalse()
      expect(policy.release(BackgroundTaskOwner.Video)).assertFalse()
      expect(policy.release(BackgroundTaskOwner.Music)).assertTrue()
      expect(policy.release(BackgroundTaskOwner.Music)).assertFalse()
    })
  })
}
