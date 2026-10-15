import { describe, expect, it } from '@ohos/hypium'
import {
  ManagementNativePagePolicy
} from '../main/ets/features/management/ManagementNativePagePolicy'

export default function managementNativePagePolicyTest() {
  describe('ManagementNativePagePolicy', () => {
    it('usesExpandedLayoutAt840Vp', 0, () => {
      expect(ManagementNativePagePolicy.isExpanded(839)).assertFalse()
      expect(ManagementNativePagePolicy.isExpanded(840)).assertTrue()
    })

    it('allocatesBottomInsetOnce', 0, () => {
      expect(ManagementNativePagePolicy.contentBottomPadding(24, false)).assertEqual(24)
      expect(ManagementNativePagePolicy.contentBottomPadding(24, true)).assertEqual(96)
      expect(ManagementNativePagePolicy.contentBottomPadding(-10, true)).assertEqual(72)
    })
  })
}
