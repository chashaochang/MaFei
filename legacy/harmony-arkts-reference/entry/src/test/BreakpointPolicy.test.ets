import { describe, expect, it } from '@ohos/hypium'
import { BreakpointPolicy, BreakpointTypeEnum } from '../main/ets/utils/BreakpointPolicy'

export default function breakpointPolicyTest() {
  describe('BreakpointPolicy', () => {
    it('mapsEveryBoundaryFromBothSides', 0, () => {
      expect(BreakpointPolicy.fromWidth(319)).assertEqual(BreakpointTypeEnum.XS)
      expect(BreakpointPolicy.fromWidth(320)).assertEqual(BreakpointTypeEnum.SM)
      expect(BreakpointPolicy.fromWidth(599)).assertEqual(BreakpointTypeEnum.SM)
      expect(BreakpointPolicy.fromWidth(600)).assertEqual(BreakpointTypeEnum.MD)
      expect(BreakpointPolicy.fromWidth(839)).assertEqual(BreakpointTypeEnum.MD)
      expect(BreakpointPolicy.fromWidth(840)).assertEqual(BreakpointTypeEnum.LG)
      expect(BreakpointPolicy.fromWidth(1439)).assertEqual(BreakpointTypeEnum.LG)
      expect(BreakpointPolicy.fromWidth(1440)).assertEqual(BreakpointTypeEnum.XL)
      expect(BreakpointPolicy.fromWidth(1919)).assertEqual(BreakpointTypeEnum.XL)
      expect(BreakpointPolicy.fromWidth(1920)).assertEqual(BreakpointTypeEnum.XXL)
    })

    it('keepsNonPositiveWidthsInXs', 0, () => {
      expect(BreakpointPolicy.fromWidth(0)).assertEqual(BreakpointTypeEnum.XS)
      expect(BreakpointPolicy.fromWidth(-1)).assertEqual(BreakpointTypeEnum.XS)
    })
  })
}
