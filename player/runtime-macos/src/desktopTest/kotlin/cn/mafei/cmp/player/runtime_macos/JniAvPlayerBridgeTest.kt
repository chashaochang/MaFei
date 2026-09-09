package cn.mafei.cmp.player.runtime_macos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real JNI linkage proof on the Apple Silicon build host: the bundled dylib
 * loads, an AVPlayer can be created and torn down, and the query surface
 * answers without playing any media. No network, no audio.
 */
class JniAvPlayerBridgeTest {

    @Test
    fun bridgeCreatesAndReleasesARealAvPlayer() {
        val bridge = JniAvPlayerBridge()
        val handle = bridge.create()
        assertTrue(handle != 0L, "AVPlayer handle must be a live native reference")
        try {
            // No item loaded yet: unknown status, zero duration, paused.
            assertEquals(0, bridge.itemStatus(handle))
            assertEquals(0, bridge.durationMs(handle))
            assertEquals(0, bridge.timeControl(handle))
            // A relative URL without a scheme must not crash the query surface.
            bridge.loadUrl(handle, "not-a-valid-url", headers = emptyMap())
            bridge.releasePlayer(handle)
        } finally {
            bridge.releasePlayer(handle)
        }
    }
}
