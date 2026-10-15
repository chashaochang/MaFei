package cn.mafei.cmp.providers.jellyfin.core

import kotlin.test.Test
import kotlin.test.assertEquals

class JellyfinAuthorizationHeaderTest {

    @Test
    fun usesTheOfficialParameterOrder() {
        assertEquals(
            "MediaBrowser Client=\"MaFei\", Version=\"2.0.0\", DeviceId=\"id-1\", Device=\"Test \\\"Device\\\"\"",
            JellyfinAuthorizationHeader.format("MaFei", "Test \"Device\"", "id-1", "2.0.0"),
        )
        assertEquals(
            "MediaBrowser Client=\"MaFei\", Version=\"1.0\", DeviceId=\"id\", Device=\"d\", Token=\"tk\"",
            JellyfinAuthorizationHeader.format("MaFei", "d", "id", "1.0", token = "tk"),
        )
        // blank token omits the Token part entirely
        assertEquals(
            "MediaBrowser Client=\"M\", Version=\"1\", DeviceId=\"i\", Device=\"d\"",
            JellyfinAuthorizationHeader.format("M", "d", "i", "1", token = "  "),
        )
    }

    @Test
    fun backslashesEscapeBeforeQuotes() {
        assertEquals(
            "MediaBrowser Client=\"a\\\\b\", Version=\"1\", DeviceId=\"i\", Device=\"d\"",
            JellyfinAuthorizationHeader.format("a\\b", "d", "i", "1"),
        )
    }
}
