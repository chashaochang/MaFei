package cn.mafei.cmp.providers.jellyfin.images

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import cn.mafei.cmp.providers.jellyfin.core.InMemoryJellyfinSessionState
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig
import cn.mafei.cmp.providers.jellyfin.core.JellyfinSession
import cn.mafei.cmp.providers.jellyfin.wire.ImageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JellyfinImagesApiTest {

    private val base: HttpUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL)

    private fun api(withSession: Boolean): DefaultJellyfinImagesApi {
        val session = if (withSession) JellyfinSession("u1", "tok-1") else null
        return DefaultJellyfinImagesApi(
            JellyfinClient(
                JellyfinServerConfig(base, "MaFei", "Test", "device-1", "2.0.0"),
                FakeJellyfinTransport(),
                InMemoryJellyfinSessionState(session),
            ),
        )
    }

    @Test
    fun itemImageUrlDefaultsToAnonymousPrimary() {
        val url = api(withSession = true).itemImageUrl("m1", maxWidth = 300, tag = "t1")
        assertEquals("https://jellyfin.test/Items/m1/Images/Primary?maxWidth=300&tag=t1", url.toString())
        assertNull(url.queryParameters.first("api_key"))
    }

    @Test
    fun itemImageUrlAppendsApiKeyOnlyWhenRequested() {
        val withKey = api(withSession = true).itemImageUrl("m1", imageType = ImageType.Backdrop, appendApiKey = true)
        assertEquals("tok-1", withKey.queryParameters.first("api_key"))

        val withoutSession = api(withSession = false).itemImageUrl("m1", appendApiKey = true)
        assertNull(withoutSession.queryParameters.first("api_key"))
    }

    @Test
    fun userImageUrlCarriesTagAndToken() {
        val url = api(withSession = true).userImageUrl("u1", tag = "imgtag", appendApiKey = true)
        assertEquals("https://jellyfin.test/Users/u1/Images/Primary?tag=imgtag&api_key=tok-1", url.toString())
    }
}
