import { describe, expect, it } from '@ohos/hypium';
import { UserPolicy } from 'sdk/src/main/ets/generated-client/models';
import { ManagementProfileDraft } from '../../../main/ets/features/management/ManagementUserModels';
import { ManagementUserPolicy } from '../../../main/ets/features/management/ManagementUserPolicy';
import { ObjectUtil } from '../../../main/ets/utils/ObjectUtil';

export default function managementUserProtectionTest() {
  describe('ManagementUserProtection', () => {
    it('blocksSelfDemotion', 0, () => {
      const result = ManagementUserPolicy.canChangeAdministratorState(
        'admin', 'admin', true, true, false, 2)
      expect(result.allowed).assertFalse()
    })

    it('blocksLastEnabledAdministratorDemotion', 0, () => {
      const result = ManagementUserPolicy.canChangeAdministratorState(
        'other-admin', 'admin', true, true, false, 1)
      expect(result.allowed).assertFalse()
    })

    it('preservesUnknownPolicyFields', 0, () => {
      const current = ObjectUtil.Assign({}, {
        AuthenticationProviderId: 'default',
        PasswordResetProviderId: 'default',
        FutureServerFlag: true
      }) as UserPolicy
      const draft = new ManagementProfileDraft()
      draft.isHidden = true
      const patched = ManagementUserPolicy.patchProfile(current, draft)
      expect(JSON.stringify(patched).includes('\"FutureServerFlag\":true')).assertTrue()
    })
  })
}
