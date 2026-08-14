import { UIConstants } from '../../common/UIConstants'
import { HomeShellKind } from '../home/HomeShellPolicy'

export interface MusicHostVisibilityInput {
  queueSize: number
  isLogin: boolean
  isConnecting: boolean
  loginVisible: boolean
  videoPlayerVisible: boolean
  livePlayerVisible: boolean
  musicPlayerVisible: boolean
  photoViewerVisible: boolean
}

export class MusicHostVisibilityPolicy {
  static readonly NATIVE_MINI_PLAYER_HEIGHT: number = 52
  static readonly LEGACY_MINI_PLAYER_HEIGHT: number = 64
  static readonly MINI_PLAYER_GAP: number = 8
  static readonly COMPACT_HOME_BUTTON_SIZE: number = 56
  static readonly COMPACT_HOME_SIDE_MARGIN: number = 12
  static readonly COMPACT_HOME_GAP: number = 16

  static shouldShow(input: MusicHostVisibilityInput): boolean {
    return input.queueSize > 0 && input.isLogin && !input.isConnecting &&
      !input.loginVisible && !input.videoPlayerVisible &&
      !input.livePlayerVisible && !input.musicPlayerVisible &&
      !input.photoViewerVisible
  }

  static miniPlayerHeight(nativeSurface: boolean): number {
    return nativeSurface ? MusicHostVisibilityPolicy.NATIVE_MINI_PLAYER_HEIGHT :
      MusicHostVisibilityPolicy.LEGACY_MINI_PLAYER_HEIGHT
  }

  static occupiedHeight(nativeSurface: boolean = true): number {
    return MusicHostVisibilityPolicy.miniPlayerHeight(nativeSurface) +
      MusicHostVisibilityPolicy.MINI_PLAYER_GAP
  }

  static compactHomeChrome(homeVisible: boolean, shell: HomeShellKind,
    homeSelected: boolean, homeLibraryPinned: boolean, visible: boolean): boolean {
    return visible && homeVisible && shell === HomeShellKind.PhoneNativeHds &&
      homeSelected && homeLibraryPinned
  }

  static miniPlayerBottomOffset(homeVisible: boolean, shell: HomeShellKind,
    safeBottom: number, visible: boolean, compactHomeChrome: boolean = false): number {
    if (!visible) {
      return 0
    }
    const normalizedSafeBottom = Math.max(0, safeBottom)
    const homeNavigationHeight = !compactHomeChrome && homeVisible &&
      (shell === HomeShellKind.PhoneStandard || shell === HomeShellKind.PhoneNativeHds) ?
      UIConstants.BOTTOM_BAR_HEIGHT : 0
    return normalizedSafeBottom + homeNavigationHeight +
      MusicHostVisibilityPolicy.MINI_PLAYER_GAP
  }

  static compactPlayerWidth(deviceWidth: number): number {
    const normalizedWidth = Math.max(0, deviceWidth)
    return Math.min(328, Math.max(210, normalizedWidth -
      MusicHostVisibilityPolicy.COMPACT_HOME_SIDE_MARGIN * 2 -
      MusicHostVisibilityPolicy.COMPACT_HOME_BUTTON_SIZE -
      MusicHostVisibilityPolicy.COMPACT_HOME_GAP))
  }
}
