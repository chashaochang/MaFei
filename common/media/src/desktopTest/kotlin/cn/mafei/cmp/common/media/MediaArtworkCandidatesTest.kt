package cn.mafei.cmp.common.media

import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaArtworkCandidatesTest {

    @Test
    fun posterKeepsThumbAndBackdropAfterPrimarySoRuntimeCanRecoverFrom404() {
        val item = item(
            MediaImage(ImageKind.Backdrop, "backdrop"),
            MediaImage(ImageKind.Primary, "primary"),
            MediaImage(ImageKind.Thumb, "thumb"),
            MediaImage(ImageKind.Primary, "primary"),
        )

        assertEquals(
            listOf("primary", "thumb", "backdrop"),
            item.posterArtworkCandidates().map { it.url },
        )
    }

    @Test
    fun landscapePrefersBackdropWithoutLosingPosterFallbacks() {
        val item = item(
            MediaImage(ImageKind.Primary, "primary"),
            MediaImage(ImageKind.Thumb, "thumb"),
            MediaImage(ImageKind.Backdrop, "backdrop"),
        )

        assertEquals(
            listOf("backdrop", "thumb", "primary"),
            item.landscapeArtworkCandidates().map { it.url },
        )
    }

    private fun item(vararg images: MediaImage) = MediaItem(
        id = MediaId("item"),
        serverId = ServerId("server"),
        provider = ProviderKind.Jellyfin,
        title = "Title",
        images = images.toList(),
    )
}
