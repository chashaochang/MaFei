package cn.mafei.cmp.session

import kotlin.test.*
import kotlinx.serialization.json.*
import cn.mafei.cmp.core.serialization.MafeiJson

class WebPageRequestTest {
    @Test fun webServerAddressMatchesJellyfinWebSingletonAtRootAndUnderBasePath() {
        for (base in listOf("https://nas.test/", "https://nas.test/jellyfin/")) {
            val address = base.trimEnd('/')
            val request = WebPageRequest.jellyfin(base, "server", "Test", "user", "token",
                buildJsonObject { put("Id", "user") }, "device", 12345)
            assertTrue(request.bootstrapScript.contains("\"ManualAddress\":\"$address\""))
            assertTrue(request.bootstrapScript.contains("\"LocalAddress\":\"$address\""))
            assertEquals(address, request.url)
        }
    }

    @Test fun webDeviceInformationCanBeUsedInBrowserAuthorizationHeaders() {
        val request = WebPageRequest.jellyfin("https://nas.test", "server", "Test", "user", "token",
            buildJsonObject { put("Id", "user") }, "device", 12345)
        val device = Json.parseToJsonElement(request.deviceInformation).jsonObject
        for (field in listOf("deviceId", "deviceName", "appName", "appVersion")) {
            val value = device.getValue(field).jsonPrimitive.content
            assertTrue(value.isNotBlank())
            assertTrue(value.all { it.code in 0x20..0x7e }, "$field must be safe for browser HTTP headers")
        }
    }

    @Test fun returnLocationCannotMoveAccountCredentialsToAnotherServer() {
        val request = WebPageRequest("https://nas.test/jellyfin", "Web", "secret")
        assertEquals("https://nas.test/jellyfin/web/#/details?id=movie", request.atUrl("https://nas.test/jellyfin/web/#/details?id=movie").url)
        assertFailsWith<IllegalArgumentException> { request.atUrl("https://other.test/web/") }
        val script = request.messageBridgeScript("MaFeiBridge")
        assertTrue(script.contains("window.top !== window")); assertTrue(script.contains("location.origin !== \"https://nas.test\""))
        assertFailsWith<IllegalArgumentException> { request.messageBridgeScript("x;alert(1)") }
    }
    @Test fun bootstrapKeepsLegacyKeysAndCredentialsStayTransientAndRedacted() {
        val request = WebPageRequest.jellyfin("https://nas.test/jellyfin", "server", "Test", "user", "secret-token",
            buildJsonObject { put("Id", "user"); put("ServerId", "server") }, "device", 12345)
        assertTrue(request.bootstrapScript.contains("jellyfin_credentials"))
        assertTrue(request.bootstrapScript.contains("user-user-server"))
        assertTrue(request.bootstrapScript.contains("\"LastConnectionMode\":2"))
        assertTrue(request.bootstrapScript.contains("\"ManualAddress\":\"https://nas.test/jellyfin\""))
        assertTrue(request.bootstrapScript.contains("location.origin !== \"https://nas.test\""))
        assertFalse(request.toString().contains("secret-token"))
        assertTrue(request.accepts("https://nas.test:443/jellyfin/web"))
        assertFalse(request.accepts("http://nas.test/jellyfin")); assertFalse(request.accepts("https://nas.test.evil/jellyfin"))
    }

    @Test fun webPlaybackUsesLegacyTicksAndProviderTrackIndexes() {
        val request = MafeiJson.Default.decodeFromString(WebNativePlaybackRequest.serializer(),
            """{"ids":["first","second"],"startIndex":1,"startPositionTicks":123450000,"audioStreamIndex":4,"subtitleStreamIndex":-1}""")
        assertEquals(12345, request.startMs); assertEquals(1, request.startIndex)
        assertEquals(4, request.options().audioStreamIndex); assertEquals(-1, request.options().subtitleStreamIndex)
    }

    @Test fun malformedQueuesNeverReachThePlayer() {
        assertFailsWith<IllegalArgumentException> { WebNativePlaybackRequest(emptyList()) }
        assertFailsWith<IllegalArgumentException> { WebNativePlaybackRequest(listOf("id"), 2) }
        assertFailsWith<IllegalArgumentException> { WebNativePlaybackRequest(listOf("id"), startPositionTicks = -1) }
        assertFailsWith<IllegalArgumentException> { WebNativePlaybackRequest(listOf("id"), subtitleStreamIndex = -2) }
    }
}
