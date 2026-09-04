import { BreakpointType } from "../utils/BreakpointSystem"

export const HORIZONTAL_CARD_WIDTH: BreakpointType<Resource> = new BreakpointType<Resource>({
  xs: $r('app.float.horizontal_card_width_sm'),
  sm: $r('app.float.horizontal_card_width_sm'),
  md: $r('app.float.horizontal_card_width_lg'),
  lg: $r('app.float.horizontal_card_width_lg'),
  xl: $r('app.float.horizontal_card_width_lg'),
  xxl: $r('app.float.horizontal_card_width_lg'),
})

export const HORIZONTAL_CARD_HEIGHT: BreakpointType<Resource> = new BreakpointType<Resource>({
  xs: $r('app.float.horizontal_card_height_sm'),
  sm: $r('app.float.horizontal_card_height_sm'),
  md: $r('app.float.horizontal_card_height_lg'),
  lg: $r('app.float.horizontal_card_height_lg'),
  xl: $r('app.float.horizontal_card_height_lg'),
  xxl: $r('app.float.horizontal_card_height_lg'),
})

export const HORIZONTAL_CARD_LIST_HEIGHT: BreakpointType<Resource> = new BreakpointType<Resource>({
  xs: $r('app.float.horizontal_card_list_height_sm'),
  sm: $r('app.float.horizontal_card_list_height_sm'),
  md: $r('app.float.horizontal_card_list_height_lg'),
  lg: $r('app.float.horizontal_card_list_height_lg'),
  xl: $r('app.float.horizontal_card_list_height_lg'),
  xxl: $r('app.float.horizontal_card_list_height_lg'),
})

export const HORIZONTAL_CARD_SPACE: BreakpointType<number> = new BreakpointType<number>({
  xs: 8,
  sm: 8,
  md: 16,
  lg: 16,
  xl: 16,
  xxl: 16,
})

export const VERTICAL_CARD_WIDTH: BreakpointType<Resource> = new BreakpointType<Resource>({
  xs: $r('app.float.vertical_card_width_sm'),
  sm: $r('app.float.vertical_card_width_sm'),
  md: $r('app.float.vertical_card_width_lg'),
  lg: $r('app.float.vertical_card_width_lg'),
  xl: $r('app.float.vertical_card_width_lg'),
  xxl: $r('app.float.vertical_card_width_lg'),
})

export const VERTICAL_CARD_HEIGHT: BreakpointType<Resource> = new BreakpointType<Resource>({
  xs: $r('app.float.vertical_card_height_sm'),
  sm: $r('app.float.vertical_card_height_sm'),
  md: $r('app.float.vertical_card_height_lg'),
  lg: $r('app.float.vertical_card_height_lg'),
  xl: $r('app.float.vertical_card_height_lg'),
  xxl: $r('app.float.vertical_card_height_lg'),
})

export const VERTICAL_CARD_LIST_HEIGHT: BreakpointType<Resource> = new BreakpointType<Resource>({
  xs: $r('app.float.vertical_card_list_height_sm'),
  sm: $r('app.float.vertical_card_list_height_sm'),
  md: $r('app.float.vertical_card_list_height_lg'),
  lg: $r('app.float.vertical_card_list_height_lg'),
  xl: $r('app.float.vertical_card_list_height_lg'),
  xxl: $r('app.float.vertical_card_list_height_lg'),
})