import { UIConstants } from '../../common/UIConstants'
import { MusicHostVisibilityPolicy } from '../music/MusicHostVisibilityPolicy'
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

  static contentBottomInset(shell: HomeShellKind, safeBottom: number,
    miniPlayerVisible: boolean = false, nativeMiniPlayer: boolean = true): number {
    const normalizedSafeBottom = HomeInsetsPolicy.nonNegative(safeBottom)
    const miniPlayerInset = miniPlayerVisible ?
      MusicHostVisibilityPolicy.occupiedHeight(nativeMiniPlayer) : 0
    if (shell === HomeShellKind.PhoneStandard || shell === HomeShellKind.PhoneNativeHds) {
      return UIConstants.BOTTOM_BAR_HEIGHT + normalizedSafeBottom + miniPlayerInset
    }
    return normalizedSafeBottom + miniPlayerInset
  }

  static scrollTail(contentBottomInset: number | undefined, safeBottom: number): number {
    const ownedInset = contentBottomInset === undefined ?
      HomeInsetsPolicy.nonNegative(safeBottom) :
      HomeInsetsPolicy.nonNegative(contentBottomInset)
    return ownedInset + 16
  }
}
