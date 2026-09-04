export enum BreakpointTypeEnum {
  XS = 'xs',
  SM = 'sm',
  MD = 'md',
  LG = 'lg',
  XL = 'xl',
  XXL = 'xxl'
}

export class BreakpointPolicy {
  static fromWidth(width: number): BreakpointTypeEnum {
    if (width < 320) {
      return BreakpointTypeEnum.XS
    }
    if (width < 600) {
      return BreakpointTypeEnum.SM
    }
    if (width < 840) {
      return BreakpointTypeEnum.MD
    }
    if (width < 1440) {
      return BreakpointTypeEnum.LG
    }
    if (width < 1920) {
      return BreakpointTypeEnum.XL
    }
    return BreakpointTypeEnum.XXL
  }
}
