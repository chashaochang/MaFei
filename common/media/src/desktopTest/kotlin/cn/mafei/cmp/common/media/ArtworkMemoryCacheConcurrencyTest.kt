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
import kotlin.test.assertNull

class ArtworkMemoryCacheConcurrencyTest {

    @Test
    fun networkFetchesDoNotConsumeTheTwoDecoderSlots() = runBlocking {
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
}
