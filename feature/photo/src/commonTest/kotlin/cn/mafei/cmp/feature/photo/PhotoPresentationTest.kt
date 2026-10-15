package cn.mafei.cmp.feature.photo

import cn.mafei.cmp.core.domain.*
import kotlin.test.*

class PhotoPresentationTest {
    private fun photo(id: String, day: String) = MediaItem(MediaId(id), ServerId("s"), ProviderKind.Jellyfin,
        id, MediaType.Photo, photo = PhotoInfo(id, dateKey = day, fullImage = MediaImage(url = "https://test/$id")))

    @Test fun newestDateGroupsFirstUnknownLastStableWithinDay() {
        val groups = photoDateGroups(listOf(photo("u", "unknown"), photo("a", "2026-10-08"),
            photo("b", "2026-10-09"), photo("c", "2026-10-08")))
        assertEquals(listOf("2026-10-09", "2026-10-08", "unknown"), groups.map { it.first })
        assertEquals(listOf("a", "c"), groups[1].second.map { it.id.value })
    }

    @Test fun zoomUsesFittedPictureBoundsAndResetsAtOne() {
        assertEquals(PhotoTransform(2f, 150f, 0f), clampPhotoTransform(2f, 999f, 999f, 300f, 600f, 600f, 300f))
        assertEquals(PhotoTransform(), clampPhotoTransform(.1f, 999f, 999f, 300f, 600f, 600f, 300f))
        assertEquals(5f, clampPhotoTransform(7f, 0f, 0f, 300f, 600f, 600f, 300f).scale)
    }

    @Test fun malformedTransformNeverProducesNan() {
        assertEquals(PhotoTransform(), clampPhotoTransform(Float.NaN, Float.NaN, Float.POSITIVE_INFINITY,
            300f, 600f, 0f, 0f))
    }
}
