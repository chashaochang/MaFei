package cn.mafei.cmp

import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PlatformArtworkTest {
    @Test fun usesAuthenticatedLoaderAndFallsBackInTheExistingImageOrder() = runBlocking<Unit> {
        val images = listOf(MediaImage(url = "https://media.invalid/poster", kind = ImageKind.Primary),
            MediaImage(url = "https://media.invalid/thumb", kind = ImageKind.Thumb))
        val requested = mutableListOf<MediaImage>()
        val result = loadPlatformArtwork(images) { image, width ->
            requested += image
            assertEquals(480, width)
            if (image === images.first()) null else byteArrayOf(1, 2, 3)
        }
        assertEquals(images, requested)
        assertContentEquals(byteArrayOf(1, 2, 3), result)
    }

    @Test fun skipsUnavailableAndOversizedImagesWithoutBreakingPlayback() = runBlocking<Unit> {
        val images = (0..2).map { MediaImage(url = "https://media.invalid/$it", kind = ImageKind.Primary) }
        val result = loadPlatformArtwork(images) { image, _ ->
            when (image) {
                images[0] -> throw IllegalStateException("image unavailable")
                images[1] -> ByteArray(4 * 1024 * 1024 + 1)
                else -> byteArrayOf(7)
            }
        }
        assertContentEquals(byteArrayOf(7), result)
    }

    @Test fun accountOrPlaybackCancellationDoesNotContinueLoadingAnotherImage() = runBlocking<Unit> {
        val image = MediaImage(url = "https://media.invalid/poster", kind = ImageKind.Primary)
        assertFailsWith<CancellationException> {
            loadPlatformArtwork(listOf(image)) { _, _ -> throw CancellationException("session retired") }
        }
    }
}
