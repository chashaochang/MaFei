package cn.mafei.cmp.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class MediaModelsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val item = MediaItem(
        id = MediaId("ep-1"),
        serverId = ServerId("srv-1"),
        provider = ProviderKind.Jellyfin,
        mediaType = MediaType.Episode,
        title = "Pilot",
        originalTitle = "Pilot (Original)",
        overview = "First episode.",
        collectionId = CollectionId("col-1"),
        seriesId = MediaId("series-1"),
        seasonId = MediaId("season-1"),
        seasonNumber = 1,
        episodeNumber = 1,
        year = 2024,
        premiereEpochMs = 1_704_067_200_000L,
        dateAddedEpochMs = 1_704_067_300_000L,
        runtimeMs = 2_400_000L,
        communityRating = 8.5,
        officialRating = "TV-14",
        genres = listOf("Drama", "Sci-Fi"),
        people = listOf(PersonRef(name = "Ada", role = "Director", personType = PersonKind.Director)),
        images = listOf(MediaImage(url = "img://primary", widthPx = 600, heightPx = 900)),
        userData = MediaUserData(
            isFavorite = true,
            isPlayed = false,
            playCount = 2,
            playbackPositionMs = 60_000L,
            playedPercentage = 2.5,
            lastPlayedEpochMs = 1_704_067_400_000L,
        ),
        canPlay = true,
    )

    @Test
    fun mediaItemRoundTripsThroughJson() {
        val encoded = json.encodeToString(MediaItem.serializer(), item)
        val decoded = json.decodeFromString(MediaItem.serializer(), encoded)
        assertEquals(item, decoded)
    }

    @Test
    fun mediaItemIgnoresUnknownFields() {
        val payload = """
            {
              "id": "ep-1",
              "serverId": "srv-1",
              "provider": "Jellyfin",
              "title": "Pilot",
              "someNewVendorField": { "nested": true },
              "anotherFutureField": [1, 2, 3]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(MediaItem.serializer(), payload)
        assertEquals("Pilot", decoded.title)
        assertEquals(MediaType.Unknown, decoded.mediaType)
        assertEquals(MediaUserData(), decoded.userData)
        assertNull(decoded.year)
    }

    @Test
    fun mediaItemDefaultsForMinimalPayload() {
        val decoded = json.decodeFromString(
            MediaItem.serializer(),
            """{"id":"m-1","serverId":"srv-1","provider":"Feiniu","title":"Movie"}""",
        )
        assertEquals(MediaType.Unknown, decoded.mediaType)
        assertEquals(ProviderKind.Feiniu, decoded.provider)
        assertEquals(emptyList(), decoded.genres)
        assertEquals(false, decoded.canPlay)
    }

    @Test
    fun collectionAndAccountRoundTrip() {
        val collection = MediaCollection(
            id = CollectionId("col-1"),
            serverId = ServerId("srv-1"),
            provider = ProviderKind.Feiniu,
            name = "Movies",
            mediaTypes = setOf(MediaType.Movie, MediaType.Folder),
            primaryImage = MediaImage(url = "img://col", widthPx = 400, heightPx = 600),
        )
        val account = Account(
            id = AccountId("acc-1"),
            serverId = ServerId("srv-1"),
            provider = ProviderKind.Jellyfin,
            userName = "ada",
            displayName = "Ada L.",
            isCurrent = true,
            lastActiveEpochMs = 1_704_067_200_000L,
        )
        val server = Server(
            id = ServerId("srv-1"),
            provider = ProviderKind.Jellyfin,
            name = "Home",
            baseUrl = "https://media.example.org",
            serverVersion = "10.9.2",
        )

        for (model in listOf(collection, account, server)) {
            val serializer = when (model) {
                is MediaCollection -> MediaCollection.serializer()
                is Account -> Account.serializer()
                else -> Server.serializer()
            }
            @Suppress("UNCHECKED_CAST")
            val typed = serializer as kotlinx.serialization.KSerializer<Any>
            val encoded = json.encodeToString(typed, model)
            val decoded = when (model) {
                is MediaCollection -> json.decodeFromString(MediaCollection.serializer(), encoded)
                is Account -> json.decodeFromString(Account.serializer(), encoded)
                else -> json.decodeFromString(Server.serializer(), encoded)
            }
            assertEquals(model, decoded)
        }
    }

    @Test
    fun accountDefaultsDisplayNameToUserName() {
        val account = Account(
            id = AccountId("acc-1"),
            serverId = ServerId("srv-1"),
            provider = ProviderKind.Feiniu,
            userName = "feiniu-user",
        )
        assertEquals("feiniu-user", account.displayName)
    }
}
