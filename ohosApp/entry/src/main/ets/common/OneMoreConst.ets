import { AppStorageV2 } from "@kit.ArkUI"
import { AppUIState } from "../entity/AppUIState"

export const vp_4: number = getSizeByBreakPoint(4)
export const vp_8: number = getSizeByBreakPoint(8)
export const vp_12: number = getSizeByBreakPoint(12)
export const vp_16: number = getSizeByBreakPoint(16)
export const vp_20: number = getSizeByBreakPoint(20)
export const vp_24: number = getSizeByBreakPoint(24)
export const vp_32: number = getSizeByBreakPoint(32)
export const vp_40: number = getSizeByBreakPoint(40)
export const vp_48: number = getSizeByBreakPoint(48)
export const vp_56: number = getSizeByBreakPoint(56)
export const vp_64: number = getSizeByBreakPoint(64)
export const vp_72: number = getSizeByBreakPoint(72)
export const vp_80: number = getSizeByBreakPoint(80)
export const vp_88: number = getSizeByBreakPoint(88)
export const vp_90: number = getSizeByBreakPoint(90)
export const vp_96: number = getSizeByBreakPoint(96)
export const vp_100: number = getSizeByBreakPoint(100)
export const vp_104: number = getSizeByBreakPoint(104)
export const vp_112: number = getSizeByBreakPoint(112)
export const vp_120: number = getSizeByBreakPoint(120)
export const vp_128: number = getSizeByBreakPoint(128)
export const vp_134: number = getSizeByBreakPoint(134)
export const vp_136: number = getSizeByBreakPoint(136)
export const vp_144: number = getSizeByBreakPoint(144)
export const vp_150: number = getSizeByBreakPoint(150)
export const vp_152: number = getSizeByBreakPoint(152)
export const vp_160: number = getSizeByBreakPoint(160)
export const vp_168: number = getSizeByBreakPoint(168)
export const vp_176: number = getSizeByBreakPoint(176)
export const vp_184: number = getSizeByBreakPoint(184)
export const vp_192: number = getSizeByBreakPoint(192)
export const vp_194: number = getSizeByBreakPoint(194)
export const vp_200: number = getSizeByBreakPoint(200)

function getSizeByBreakPoint(sm: number):number {
  let appUIState:AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  let bp = appUIState.currentBreakpoint
  return bp.includes('l') ? sm * 1.5 : sm
}
