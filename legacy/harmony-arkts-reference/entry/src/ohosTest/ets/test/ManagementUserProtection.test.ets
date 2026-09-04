import { describe, expect, it } from '@ohos/hypium';
import { UserPolicy } from 'sdk/src/main/ets/generated-client/models';
import {
  ManagementAccessScheduleItem,
  ManagementParentalDraft,
  ManagementProfileDraft
} from '../../../main/ets/features/management/ManagementUserModels';
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

    it('blocksCurrentAdministratorDeletion', 0, () => {
      const result = ManagementUserPolicy.canDelete(
        'admin', 'admin', true, true, 2)
      expect(result.allowed).assertFalse()
    })

    it('blocksLastEnabledAdministratorDeletion', 0, () => {
      const result = ManagementUserPolicy.canDelete(
        'other-admin', 'admin', true, true, 1)
      expect(result.allowed).assertFalse()
    })

    it('allowsAdministratorDeletionWhenAnotherEnabledAdministratorRemains', 0, () => {
      const result = ManagementUserPolicy.canDelete(
        'other-admin', 'admin', true, true, 2)
      expect(result.allowed).assertTrue()
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

    it('normalizesDuplicateParentalTags', 0, () => {
      const normalized = ManagementUserPolicy.normalizeTags([' family ', 'family', '', 'kids'])
      expect(normalized.length).assertEqual(2)
      expect(normalized[0]).assertEqual('family')
      expect(normalized[1]).assertEqual('kids')
    })

    it('rejectsAllowedAndBlockedTagConflicts', 0, () => {
      const draft = new ManagementParentalDraft()
      draft.allowedTags = ['family']
      draft.blockedTags = ['family']
      expect(ManagementUserPolicy.validateParental(draft))
        .assertEqual('management_user_parental_conflicting_tags')
    })

    it('rejectsInvalidAccessSchedules', 0, () => {
      const draft = new ManagementParentalDraft()
      const schedule = new ManagementAccessScheduleItem()
      schedule.startHour = 20
      schedule.endHour = 8
      draft.schedules = [schedule]
      expect(ManagementUserPolicy.validateParental(draft))
        .assertEqual('management_user_parental_invalid_schedule')
    })

    it('copiesParentalArraysAndSchedules', 0, () => {
      const current = ObjectUtil.Assign({}, {}) as UserPolicy
      const draft = new ManagementParentalDraft()
      draft.allowedTags = ['family']
      draft.blockedUnratedItems = ['Movie']
      const schedule = new ManagementAccessScheduleItem()
      schedule.startHour = 8
      schedule.endHour = 18
      draft.schedules = [schedule]
      const patched = ManagementUserPolicy.patchParental(current, draft)
      draft.allowedTags[0] = 'changed'
      draft.blockedUnratedItems[0] = 'Other'
      draft.schedules[0].startHour = 1
      expect(patched.AllowedTags?.[0]).assertEqual('family')
      expect(patched.BlockUnratedItems?.[0]).assertEqual('Movie')
      expect(patched.AccessSchedules?.[0].StartHour).assertEqual(8)
    })
  })
}
