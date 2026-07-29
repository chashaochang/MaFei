import { UIConstants } from '../../common/UIConstants'
import { HomeShellKind } from './HomeShellPolicy'

export class HomeInsetsPolicy {
  private static nonNegative(value: number): number {
    return Math.max(0, value)
  }

  static navigationChromeInset(shell: HomeShellKind, safeBottom: number): number {
    if (shell === HomeShellKind.PhoneStandard) {
      return UIConstants.BOTTOM_BAR_HEIGHT + HomeInsetsPolicy.nonNegative(safeBottom)
    }
    return 0
  }

  static contentBottomInset(shell: HomeShellKind, safeBottom: number): number {
    const normalizedSafeBottom = HomeInsetsPolicy.nonNegative(safeBottom)
    if (shell === HomeShellKind.PhoneStandard || shell === HomeShellKind.PhoneNativeHds) {
      return UIConstants.BOTTOM_BAR_HEIGHT + normalizedSafeBottom
    }
    return normalizedSafeBottom
  }

  static scrollTail(contentBottomInset: number | undefined, safeBottom: number): number {
    const ownedInset = contentBottomInset === undefined ?
      HomeInsetsPolicy.nonNegative(safeBottom) :
      HomeInsetsPolicy.nonNegative(contentBottomInset)
    return ownedInset + 16
  }
}
