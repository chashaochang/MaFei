package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.*
import cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import kotlin.test.*

class EmbyAdapterTest {
    private val transport = FakeJellyfinTransport()
    private val adapter = JellyfinMediaProviderAdapter(
        DefaultJellyfinFacade(
            JellyfinServerConfig(
                HttpUrl.parse("https://media.test/emby"), "MaFei", "Test", "device-fixture", "2.0.0",
                MediaBrowserProtocol.Emby,
            ),
            transport, InMemoryJellyfinSessionState(JellyfinSession("user-fixture", "token-fixture")),
        ),
        ServerId("server-fixture"), AccountId("account-fixture"), "user-fixture",
    )

    @Test
    fun sharedCatalogKeepsEmbyIdentityForLibrariesItemsAndPeople() = runSuspendTest {
        transport.enqueueJson(200, """{"Items":[{"Id":"library","Name":"Movies","Type":"CollectionFolder","CollectionType":"movies"}]}""")
        transport.enqueueJson(200, """{"Items":[{"Id":"movie","Name":"Fixture Movie","Type":"Movie"}]}""")
        transport.enqueueJson(200, """{"Id":"person","Name":"Fixture Actor","Type":"Person"}""")

        val library = adapter.collections(PageRequest()).items.single()
        val movie = adapter.items(library.id, PageRequest()).items.single()
        val person = adapter.person(MediaId("person"), "Fixture Actor")

        assertEquals(listOf(ProviderKind.Emby, ProviderKind.Emby, ProviderKind.Emby),
            listOf(library.provider, movie.provider, person.provider))
        assertEquals(library.id, movie.collectionId)
        assertEquals(listOf(
            "/emby/Users/user-fixture/Views", "/emby/Users/user-fixture/Items", "/emby/Persons/Fixture%20Actor",
        ), transport.requests.map { it.url.encodedPath })
        transport.requests.forEach { assertEquals("token-fixture", it.headers.first("X-Emby-Token")) }
    }

    @Test
    fun unimplementedEmbySurfacesRemainUnavailableWithoutSendingRequests() = runSuspendTest {
        assertEquals(ProviderKind.Emby, adapter.provider)
        assertNull(adapter.management)
        assertFalse(adapter.capabilities.music)
        assertFalse(adapter.capabilities.photos)
        assertFalse(adapter.capabilities.liveTv)
        assertFalse(adapter.capabilities.mediaDelete)
        assertTrue(adapter.capabilities.search)
        assertTrue(adapter.capabilities.favoritesEdit)
        assertTrue(adapter.capabilities.watchedEdit)
        assertTrue(adapter.capabilities.playbackReporting)
        assertFalse(adapter.canDeleteMedia())
        assertFailsWith<ClientConfigError> { adapter.liveChannels(PageRequest()) }
        assertFailsWith<ClientConfigError> { adapter.photoOriginal(MediaId("photo")) }
        assertFailsWith<ClientConfigError> { adapter.deleteItems(listOf(MediaId("movie"))) }
        assertTrue(transport.requests.isEmpty())
    }
}
