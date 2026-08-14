import { describe, expect, it } from '@ohos/hypium'
import { UIConstants } from '../main/ets/common/UIConstants'
import { HomeShellKind } from '../main/ets/features/home/HomeShellPolicy'
import { MusicHostVisibilityPolicy } from '../main/ets/features/music/MusicHostVisibilityPolicy'

export default function musicHostVisibilityPolicyTest() {
  describe('MusicHostVisibilityPolicy', () => {
    it('showsOnlyForAnAuthenticatedUnsuppressedQueue', 0, () => {
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 2,
        isLogin: true,
        isConnecting: false,
        loginVisible: false,
        videoPlayerVisible: false,
        livePlayerVisible: false,
        musicPlayerVisible: false,
        photoViewerVisible: false
      })).assertTrue()

      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 2,
        isLogin: true,
        isConnecting: false,
        loginVisible: false,
        videoPlayerVisible: false,
        livePlayerVisible: false,
        musicPlayerVisible: true,
        photoViewerVisible: false
      })).assertFalse()
    })

    it('hidesWithoutAQueueOrDuringIdentityTransitions', 0, () => {
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 0,
        isLogin: true,
        isConnecting: false,
        loginVisible: false,
        videoPlayerVisible: false,
        livePlayerVisible: false,
        musicPlayerVisible: false,
        photoViewerVisible: false
      })).assertFalse()
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 1,
        isLogin: true,
        isConnecting: true,
        loginVisible: false,
        videoPlayerVisible: false,
        livePlayerVisible: false,
        musicPlayerVisible: false,
        photoViewerVisible: false
      })).assertFalse()
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 1,
        isLogin: false,
        isConnecting: false,
        loginVisible: false,
        videoPlayerVisible: false,
        livePlayerVisible: false,
        musicPlayerVisible: false,
        photoViewerVisible: false
      })).assertFalse()
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 1,
        isLogin: true,
        isConnecting: false,
        loginVisible: false,
        videoPlayerVisible: false,
        livePlayerVisible: false,
        musicPlayerVisible: false,
        photoViewerVisible: true
      })).assertFalse()
    })

    it('hidesWheneverAConflictingRouteIsVisible', 0, () => {
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 1,
        isLogin: true,
        isConnecting: false,
        loginVisible: true,
        videoPlayerVisible: false,
        livePlayerVisible: false,
        musicPlayerVisible: false,
        photoViewerVisible: false
      })).assertFalse()
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 1,
        isLogin: true,
        isConnecting: false,
        loginVisible: false,
        videoPlayerVisible: true,
        livePlayerVisible: false,
        musicPlayerVisible: false,
        photoViewerVisible: false
      })).assertFalse()
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 1,
        isLogin: true,
        isConnecting: false,
        loginVisible: false,
        videoPlayerVisible: false,
        livePlayerVisible: true,
        musicPlayerVisible: false,
        photoViewerVisible: false
      })).assertFalse()
      expect(MusicHostVisibilityPolicy.shouldShow({
        queueSize: 1,
        isLogin: true,
        isConnecting: false,
        loginVisible: false,
        videoPlayerVisible: false,
        livePlayerVisible: false,
        musicPlayerVisible: true,
        photoViewerVisible: false
      })).assertFalse()
    })

    it('placesTheMiniPlayerAboveHomeNavigationOnlyOnPhoneShells', 0, () => {
      expect(MusicHostVisibilityPolicy.miniPlayerBottomOffset(
        true, HomeShellKind.PhoneNativeHds, 12, true
      )).assertEqual(UIConstants.BOTTOM_BAR_HEIGHT + 20)
      expect(MusicHostVisibilityPolicy.miniPlayerBottomOffset(
        true, HomeShellKind.LargeSidebar, 12, true
      )).assertEqual(20)
      expect(MusicHostVisibilityPolicy.miniPlayerBottomOffset(
        false, HomeShellKind.PhoneNativeHds, 12, true
      )).assertEqual(20)
      expect(MusicHostVisibilityPolicy.miniPlayerBottomOffset(
        true, HomeShellKind.PhoneNativeHds, -12, false
      )).assertEqual(0)
      expect(MusicHostVisibilityPolicy.miniPlayerBottomOffset(
        true, HomeShellKind.PhoneNativeHds, 12, true, true
      )).assertEqual(20)
    })

    it('usesCompactChromeOnlyForThePinnedNativeHomeWithPlayback', 0, () => {
      expect(MusicHostVisibilityPolicy.compactHomeChrome(
        true, HomeShellKind.PhoneNativeHds, true, true, true
      )).assertTrue()
      expect(MusicHostVisibilityPolicy.compactHomeChrome(
        true, HomeShellKind.PhoneNativeHds, false, true, true
      )).assertFalse()
      expect(MusicHostVisibilityPolicy.compactHomeChrome(
        true, HomeShellKind.PhoneStandard, true, true, true
      )).assertFalse()
      expect(MusicHostVisibilityPolicy.compactHomeChrome(
        true, HomeShellKind.PhoneNativeHds, true, true, false
      )).assertFalse()
    })

    it('keepsTheCompactPlayerWithinTheSystemMiniBarWidth', 0, () => {
      expect(MusicHostVisibilityPolicy.compactPlayerWidth(360)).assertEqual(264)
      expect(MusicHostVisibilityPolicy.compactPlayerWidth(600)).assertEqual(328)
      expect(MusicHostVisibilityPolicy.compactPlayerWidth(200)).assertEqual(210)
    })

  })
}
