package cn.mafei.cmp.providers.jellyfin.facade

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import cn.mafei.cmp.providers.jellyfin.testkit.JellyfinFixtures
import cn.mafei.cmp.providers.jellyfin.wire.MediaStreamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JellyfinFacadeChainTest {

    private fun runTest(block: suspend () -> Unit) = runSuspendTest(block)

    private companion object {
        val base: HttpUrl = HttpUrl.parse(JellyfinFixtures.BASE_URL)
    }

    private class Fixture {
        val transport = FakeJellyfinTransport()
        val facade = DefaultJellyfinFacade(
            config = cn.mafei.cmp.providers.jellyfin.core.JellyfinServerConfig(base, "MaFei", "Test", "device-1", "2.0.0"),
            transport = transport,
        )
    }

    @Test
    fun chain1_serverInfoToLoginToViewsToItemPage() = runTest {
        val fixture = Fixture()
        with(fixture.transport) {
            enqueueJson(200, JellyfinFixtures.PUBLIC_SYSTEM_INFO_10_9, base)
            enqueueJson(200, JellyfinFixtures.PUBLIC_USERS, base)
            enqueueJson(200, JellyfinFixtures.AUTHENTICATION_RESULT, base)
            enqueueJson(200, JellyfinFixtures.VIEWS, base)
            enqueueJson(200, JellyfinFixtures.ITEMS_PAGE, base)
        }

        // 1. discover and gate the server
        val server = fixture.facade.discovery.verifiedServerInfo(base)
        assertEquals("10.9.11", server.info.version)

        // 2. pick a public user and log in
        val users = fixture.facade.auth.publicUsers()
        assertEquals("demo", users.first().name)
        val login = fixture.facade.auth.authenticateByName("demo", "correct-horse")
        assertEquals("a1b2c3d4e5f6a7b8a1b2c3d4e5f6a7b8", login.session.userId)
        assertEquals(login.session.userId, fixture.facade.session.value?.userId)

        // 3. home views
        val views = fixture.facade.catalog.views(login.session.userId)
        assertEquals(3, views.items.size)

        // 4. first page of a library
        val page = fixture.facade.catalog.items(
            cn.mafei.cmp.providers.jellyfin.transport.JellyfinItemQuery(
                userId = login.session.userId,
                parentId = views.items[0].id,
                startIndex = 0,
                limit = 50,
            ),
        )
        assertEquals(3, page.totalRecordCount)

        // authenticated calls carry the token from step 2
        val viewsHeader = fixture.transport.requests[3].headers.first("Authorization")!!
        assertTrue(viewsHeader.contains("Token=\"fixture-access-token-0123456789abcdef\""), viewsHeader)
    }

    @Test
    fun chain2_playbackInfoToStreamUrlToProgressReporting() = runTest {
        val fixture = Fixture()
        with(fixture.transport) {
            enqueueJson(200, JellyfinFixtures.AUTHENTICATION_RESULT, base)
            enqueueJson(200, JellyfinFixtures.PLAYBACK_INFO, base)
            enqueue(FakeJellyfinTransport.noContent(base))
            enqueue(FakeJellyfinTransport.noContent(base))
            enqueue(FakeJellyfinTransport.noContent(base))
        }

        val login = fixture.facade.auth.authenticateByName("demo", "correct-horse")
        val userId = login.session.userId

        // negotiate playback
        val info = fixture.facade.playback.playbackInfo(userId, "m0102030405060708", playSessionId = "session-0001")
        val directSource = info.mediaSources.first { it.supportsDirectPlay == true }
        assertNotNull(directSource)

        // build the stream URL for the negotiated source
        val streamUrl = fixture.facade.playback.directStreamUrl(
            itemId = "m0102030405060708",
            mediaSourceId = directSource.id,
            container = directSource.container,
            playSessionId = info.playSessionId,
        )
        assertTrue(streamUrl.toString().endsWith("stream.mkv?Static=true&MediaSourceId=ms0102030405060708&PlaySessionId=session-0001&api_key=fixture-access-token-0123456789abcdef"))

        // inspect track layout as the player would
        val audioTracks = directSource.mediaStreams.filter { it.type == MediaStreamType.Audio }
        val subtitleTracks = directSource.mediaStreams.filter { it.type == MediaStreamType.Subtitle }
        assertEquals(2, audioTracks.size)
        assertEquals(1, subtitleTracks.size)

        // report start -> progress -> stopped
        fixture.facade.playstate.reportStartMs(
            itemId = "m0102030405060708",
            playSessionId = info.playSessionId,
            mediaSourceId = directSource.id,
            positionMs = 0L,
            audioStreamIndex = audioTracks.first { it.isDefault == true }.index,
            subtitleStreamIndex = subtitleTracks.first().index,
            playMethod = cn.mafei.cmp.providers.jellyfin.wire.WirePlayMethod.DirectPlay,
        )
        fixture.facade.playstate.reportProgressMs("m0102030405060708", info.playSessionId, positionMs = 30_000L)
        fixture.facade.playstate.reportStoppedMs("m0102030405060708", info.playSessionId, positionMs = 45_000L)

        assertEquals(5, fixture.transport.requests.size)
        assertEquals("/Sessions/Playing/Progress", fixture.transport.requests[3].url.encodedPath)
    }

    @Test
    fun expiredTokenMidChainSurfacesAsAuthenticationExpired() = runTest {
        val fixture = Fixture()
        fixture.transport.enqueueJson(200, JellyfinFixtures.AUTHENTICATION_RESULT, base)
        fixture.transport.enqueueJson(401, JellyfinFixtures.PROBLEM_DETAILS_401, base)

        val login = fixture.facade.auth.authenticateByName("demo", "correct-horse")
        val error = kotlin.test.assertFailsWith<cn.mafei.cmp.providers.jellyfin.core.JellyfinAuthenticationExpiredError> {
            fixture.facade.catalog.views(login.session.userId)
        }
        assertEquals("Invalid token.", error.problem?.detail)
        // session state stays until the caller decides to re-login/logout
        assertNull(error.cause?.let { null } ?: error.cause)
    }
}
