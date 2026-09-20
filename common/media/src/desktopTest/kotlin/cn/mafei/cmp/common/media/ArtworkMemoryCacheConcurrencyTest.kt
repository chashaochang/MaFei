package cn.mafei.cmp.common.media

import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaImage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtworkMemoryCacheConcurrencyTest {

    @Test
    fun networkFetchesDoNotConsumeTheDecoderPermit() = runBlocking {
        val cache = ArtworkMemoryCache()
        val entered = Channel<Unit>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val requests = (0 until 3).map { index ->
            async {
                cache.load(
                    image = MediaImage(ImageKind.Primary, "https://images.test/$index"),
                    targetWidthPx = 240,
                ) { _, _ ->
                    entered.send(Unit)
                    release.await()
                    null
                }
            }
        }

        try {
            repeat(3) { withTimeout(2_000) { entered.receive() } }
        } finally {
            release.complete(Unit)
        }
        requests.awaitAll().forEach(::assertNull)
    }

    @Test
    fun missingUrlIsNegativeCachedAcrossRequestedWidths() = runBlocking {
        val cache = ArtworkMemoryCache()
        val image = MediaImage(ImageKind.Primary, "https://images.test/missing")
        var fetchCount = 0

        assertNull(cache.load(image, 160) { _, _ -> fetchCount += 1; null })
        assertNull(cache.load(image, 960) { _, _ -> fetchCount += 1; null })

        assertEquals(1, fetchCount)
    }
}
