package cn.mafei.cmp.providers.feiniu.assets

import cn.mafei.cmp.providers.feiniu.core.FeiniuCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeiniuAssetsApiTest {

    private val api = FeiniuAssetsApi()

    @Test
    fun imageUrlComposesSysImgPathWithWidth() {
        assertEquals(
            "https://feiniu.test/v/api/v1/sys/img/p/1.jpg?w=400",
            api.imageUrl("https://feiniu.test", "/p/1.jpg"),
        )
        assertEquals("", api.imageUrl("https://feiniu.test", "  "))
    }

    @Test
    fun subtitleDownloadUrlComposes() {
        assertEquals(
            "https://feiniu.test/v/api/v1/subtitle/dl/sb-0001",
            api.subtitleDownloadUrl("https://feiniu.test", "sb-0001"),
        )
        assertEquals("", api.subtitleDownloadUrl("https://feiniu.test", ""))
    }

    @Test
    fun playbackHeadersCarryTokenCookieAndRelayFlag() {
        val headers = api.playbackHeaders(FeiniuCredentials("tok", "sid=1; Trim-MC-token=tok"), relayRoute = false)
        assertEquals("tok", headers["Authorization"])
        assertEquals(true, headers["Cookie"]!!.contains("Trim-MC-token=tok"))
        assertEquals(false, headers["Cookie"]!!.contains("mode=relay"))
        // lowercase mirror for player compatibility
        assertEquals(headers["Cookie"], headers["cookie"])

        val relay = api.playbackHeaders(FeiniuCredentials("tok", ""), relayRoute = true)
        assertTrue(relay["Cookie"]!!.contains("mode=relay"))

        assertEquals(true, api.playbackHeaders(FeiniuCredentials(), relayRoute = true).isEmpty())
    }
}
