import { describe, expect, it } from '@ohos/hypium'
import { UIConstants } from '../main/ets/common/UIConstants'
import { HomeInsetsPolicy } from '../main/ets/features/home/HomeInsetsPolicy'
import { HomeShellKind } from '../main/ets/features/home/HomeShellPolicy'

export default function homeInsetsPolicyTest() {
  describe('HomeInsetsPolicy', () => {
    it('ownsNavigationChromeAndContentInsetForPhones', 0, () => {
      const expected = UIConstants.BOTTOM_BAR_HEIGHT + 24
      expect(HomeInsetsPolicy.navigationChromeInset(
        HomeShellKind.PhoneStandard,
        24
      )).assertEqual(expected)
      expect(HomeInsetsPolicy.navigationChromeInset(
        HomeShellKind.PhoneNativeHds,
        24
      )).assertEqual(expected)
      expect(HomeInsetsPolicy.contentBottomInset(
        HomeShellKind.PhoneStandard,
        24
      )).assertEqual(expected)
    })

    it('keepsNavigationChromeAtZeroButPreservesSafeBottomForWideShells', 0, () => {
      expect(HomeInsetsPolicy.navigationChromeInset(
        HomeShellKind.MediumDrawer,
        24
      )).assertEqual(0)
      expect(HomeInsetsPolicy.navigationChromeInset(
        HomeShellKind.LargeSidebar,
        24
      )).assertEqual(0)
      expect(HomeInsetsPolicy.contentBottomInset(
        HomeShellKind.MediumDrawer,
        24
      )).assertEqual(24)
      expect(HomeInsetsPolicy.contentBottomInset(
        HomeShellKind.LargeSidebar,
        24
      )).assertEqual(24)
    })

    it('clampsNegativeInsets', 0, () => {
      expect(HomeInsetsPolicy.navigationChromeInset(
        HomeShellKind.PhoneStandard,
        -8
      )).assertEqual(UIConstants.BOTTOM_BAR_HEIGHT)
      expect(HomeInsetsPolicy.contentBottomInset(
        HomeShellKind.LargeSidebar,
        -8
      )).assertEqual(0)
    })

    it('usesShellContentInsetAndStandaloneSafeBottomForScrollTails', 0, () => {
      expect(HomeInsetsPolicy.scrollTail(84, 12)).assertEqual(100)
      expect(HomeInsetsPolicy.scrollTail(24, 12)).assertEqual(40)
      expect(HomeInsetsPolicy.scrollTail(0, 12)).assertEqual(16)
      expect(HomeInsetsPolicy.scrollTail(undefined, 12)).assertEqual(28)
    })
  })
}
