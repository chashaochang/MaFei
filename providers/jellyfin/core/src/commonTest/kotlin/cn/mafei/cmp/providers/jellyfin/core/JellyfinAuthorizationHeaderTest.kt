package cn.mafei.cmp.providers.jellyfin.core

import kotlin.test.Test
import kotlin.test.assertEquals

class JellyfinAuthorizationHeaderTest {

    @Test
    fun formatsMediaBrowserHeaderWithEscaping() {
        assertEquals(
            "MediaBrowser Client=\"MaFei\", Device=\"Test \\\"Device\\\"\", DeviceId=\"id-1\", Version=\"2.0.0\"",
            JellyfinAuthorizationHeader.format("MaFei", "Test \"Device\"", "id-1", "2.0.0"),
        )
        assertEquals(
            "MediaBrowser Client=\"MaFei\", Device=\"d\", DeviceId=\"id\", Version=\"1.0\", Token=\"tk\"",
            JellyfinAuthorizationHeader.format("MaFei", "d", "id", "1.0", token = "tk"),
        )
        assertEquals(
            "MediaBrowser Client=\"M\", Device=\"d\", DeviceId=\"i\", Version=\"1\"",
            JellyfinAuthorizationHeader.format("M", "d", "i", "1", token = "  "),
        )
    }

    @Test
    fun backslashesEscapeBeforeQuotes() {
        assertEquals(
            "MediaBrowser Client=\"a\\\\b\", Device=\"d\", DeviceId=\"i\", Version=\"1\"",
            JellyfinAuthorizationHeader.format("a\\b", "d", "i", "1"),
        )
    }
}
