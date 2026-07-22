import { describe, expect, it } from '@ohos/hypium'
import { ThemeStyle } from '../main/ets/entity/ThemeStyle'
import { HomeShellKind, HomeShellPolicy } from '../main/ets/features/home/HomeShellPolicy'
import { BreakpointTypeEnum } from '../main/ets/utils/BreakpointPolicy'

export default function homeShellPolicyTest() {
  describe('HomeShellPolicy', () => {
    it('usesNativeHdsOnlyForAvailableNativePhones', 0, () => {
      expect(HomeShellPolicy.resolve(
        BreakpointTypeEnum.XS,
        ThemeStyle.Native,
        true
      )).assertEqual(HomeShellKind.PhoneNativeHds)
      expect(HomeShellPolicy.resolve(
        BreakpointTypeEnum.SM,
        ThemeStyle.Native,
        true
      )).assertEqual(HomeShellKind.PhoneNativeHds)
    })

    it('usesStandardPhoneForFeiniuAndCapabilityFailure', 0, () => {
      expect(HomeShellPolicy.resolve(
        BreakpointTypeEnum.SM,
        ThemeStyle.Feiniu,
        true
      )).assertEqual(HomeShellKind.PhoneStandard)
      expect(HomeShellPolicy.resolve(
        BreakpointTypeEnum.SM,
        ThemeStyle.Native,
        false
      )).assertEqual(HomeShellKind.PhoneStandard)
    })

    it('usesDrawerAtMdAndSidebarAtLgOrWider', 0, () => {
      expect(HomeShellPolicy.resolve(
        BreakpointTypeEnum.MD,
        ThemeStyle.Native,
        true
      )).assertEqual(HomeShellKind.MediumDrawer)
      expect(HomeShellPolicy.resolve(
        BreakpointTypeEnum.LG,
        ThemeStyle.Feiniu,
        false
      )).assertEqual(HomeShellKind.LargeSidebar)
      expect(HomeShellPolicy.resolve(
        BreakpointTypeEnum.XL,
        ThemeStyle.Native,
        true
      )).assertEqual(HomeShellKind.LargeSidebar)
      expect(HomeShellPolicy.resolve(
        BreakpointTypeEnum.XXL,
        ThemeStyle.Native,
        true
      )).assertEqual(HomeShellKind.LargeSidebar)
    })
  })
}
