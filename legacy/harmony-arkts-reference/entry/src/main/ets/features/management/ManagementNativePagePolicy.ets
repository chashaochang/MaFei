export class ManagementNativePagePolicy {
  static readonly EXPANDED_MIN_WIDTH: number = 840
  static readonly PAGE_PADDING: number = 16
  static readonly GROUP_RADIUS: number = 8
  static readonly ROW_MIN_HEIGHT: number = 56
  static readonly FLOATING_BAR_SPACE: number = 72

  static isExpanded(width: number): boolean {
    return width >= ManagementNativePagePolicy.EXPANDED_MIN_WIDTH
  }

  static contentBottomPadding(safeBottom: number, floatingBarVisible: boolean): number {
    return Math.max(0, safeBottom) +
      (floatingBarVisible ? ManagementNativePagePolicy.FLOATING_BAR_SPACE : 0)
  }
}
