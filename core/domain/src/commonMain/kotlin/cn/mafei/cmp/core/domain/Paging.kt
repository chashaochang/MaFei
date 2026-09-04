package cn.mafei.cmp.core.domain

import kotlinx.serialization.Serializable

/**
 * Opaque pagination cursor. Providers encode whatever their backend needs
 * (start index, continuation token, ...) into [value]; callers treat it as a
 * black box and simply pass it back with the next [PageRequest].
 */
@Serializable(with = PageCursorSerializer::class)
data class PageCursor(val value: String) {
    init {
        require(value.isNotBlank()) { "PageCursor must not be blank" }
    }

    override fun toString(): String = value

    data object Serializer : kotlinx.serialization.KSerializer<PageCursor> by PageCursorSerializer
}

/** Serializes [PageCursor] as its raw opaque string. */
data object PageCursorSerializer : kotlinx.serialization.KSerializer<PageCursor> {
    private val serialDescriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "PageCursor",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING,
    )

    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor
        get() = serialDescriptor

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: PageCursor) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): PageCursor =
        PageCursor(decoder.decodeString())
}

/** Sorting instruction attached to a paged query. */
@Serializable
data class MediaSort(
    val field: MediaSortField,
    val direction: SortDirection = SortDirection.Ascending,
)

/**
 * Filter applied to catalog queries. Empty collections mean "no constraint";
 * the consumer decides whether that is valid for a given screen.
 */
@Serializable
data class MediaFilter(
    val mediaTypes: Set<MediaType> = emptySet(),
    val genres: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
    val searchTerm: String? = null,
    val favoritesOnly: Boolean = false,
    val playedOnly: Boolean = false,
    val unplayedOnly: Boolean = false,
) {
    val isEmpty: Boolean
        get() = mediaTypes.isEmpty() && genres.isEmpty() && years.isEmpty() &&
            searchTerm.isNullOrBlank() && !favoritesOnly && !playedOnly && !unplayedOnly
}

/** Request for one page of results. */
@Serializable
data class PageRequest(
    val cursor: PageCursor? = null,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val sort: MediaSort? = null,
    val filter: MediaFilter = MediaFilter(),
) {
    init {
        require(pageSize in 1..MAX_PAGE_SIZE) {
            "pageSize must be within 1..$MAX_PAGE_SIZE but was $pageSize"
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 50
        const val MAX_PAGE_SIZE: Int = 500
    }
}

/**
 * One page of results plus the cursor to fetch the next one.
 *
 * Providers that only know offset paging synthesize cursors; repositories
 * merge pages without understanding their content.
 */
@Serializable
data class Page<T>(
    val items: List<T> = emptyList(),
    val nextCursor: PageCursor? = null,
    val totalEstimate: Int? = null,
) {
    val hasMore: Boolean get() = nextCursor != null
    val isEmpty: Boolean get() = items.isEmpty()

    companion object {
        fun <T> empty(): Page<T> = Page(items = emptyList(), nextCursor = null, totalEstimate = null)

        fun <T> of(
            items: List<T>,
            nextCursor: PageCursor? = null,
            totalEstimate: Int? = null,
        ): Page<T> = Page(items = items, nextCursor = nextCursor, totalEstimate = totalEstimate)
    }
}
