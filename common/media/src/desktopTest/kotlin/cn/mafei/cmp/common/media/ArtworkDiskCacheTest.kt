package cn.mafei.cmp.common.media

import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaImage
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.decode.DataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import okio.FileSystem
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertFalse

class ArtworkDiskCacheTest {
    @get:Rule val directory = TemporaryFolder()
    private lateinit var disk: DiskCache
    private val owners = mutableListOf<ArtworkMemoryCache>()
    private val image = MediaImage(ImageKind.Primary, "https://images.test/cover?tag=v1")
    private var now = 1_000_000L

    @Before fun prepareDisk() {
        disk = DiskCache.Builder().directory(directory.root.toOkioPath() / "images")
            .maxSizeBytes(4L * 1024 * 1024).build()
    }

    @After fun closeOwners() {
        owners.forEach { it.close() }
        disk.shutdown()
    }

    private fun owner(namespace: String) = ArtworkMemoryCache(
        PlatformContext.INSTANCE, namespace, disk, nowEpochMs = { now },
    ).also { owners += it }

    @Test
    fun reopeningTheSameAccountReadsDiskWithoutAnotherProviderRequest() = runBlocking {
        val first = owner("server-a/account-a")
        assertNotNull(first.load(image, 240) { _, _ -> png(120, 60) })
        first.close()
        val second = owner("server-a/account-a")
        val restored = assertNotNull(second.load(image, 240) { _, _ -> error("Must be a disk hit") })
        assertEquals(120, restored.width)
        assertSame(restored, second.peek(listOf(image), 240))
    }

    @Test
    fun accountsNeverShareTheSameUrlEntryButSwitchingBackReusesTheirOwn() = runBlocking {
        var requests = 0
        val fetch: suspend (MediaImage, Int) -> ByteArray? = { _, _ -> requests++; png(120, 60) }
        assertNotNull(owner("server-a/account-a").load(image, 240, fetch))
        assertNotNull(owner("server-a/account-b").load(image, 240, fetch))
        assertNotNull(owner("server-b/account-a").load(image, 240, fetch))
        assertNotNull(owner("server-a/account-a").load(image, 240, fetch))
        assertEquals(3, requests)
    }

    @Test
    fun imageVersionAndRequestedWidthBothDifferentiateDiskEntries() = runBlocking {
        val cache = owner("account")
        var requests = 0
        val fetch: suspend (MediaImage, Int) -> ByteArray? = { _, width -> requests++; png(width, width / 2) }
        val thumbnail = assertNotNull(cache.load(image, 240, fetch))
        assertSame(thumbnail, cache.peekAnyWidth(listOf(image), 960))
        assertEquals(960, assertNotNull(cache.load(image, 960, fetch)).width)
        assertNotNull(cache.load(image.copy(url = image.url.replace("v1", "v2")), 240, fetch))
        assertEquals(3, requests)
    }

    @Test
    fun staleDiskEntriesAreRefetched() = runBlocking {
        var requests = 0
        val fetch: suspend (MediaImage, Int) -> ByteArray? = { _, _ -> requests++; png(120, 60) }
        assertNotNull(owner("account").load(image, 240, fetch))
        now += ArtworkDiskTtlMs
        assertNotNull(owner("account").load(image, 240, fetch))
        assertEquals(2, requests)
    }

    @Test
    fun corruptImageBytesRetryTheSameCandidateThroughTheProvider() = runBlocking {
        val key = artworkCacheKey("account", image.url, 240)
        val editor = assertNotNull(disk.openEditor(key))
        disk.fileSystem.write(editor.metadata) { writeUtf8(now.toString()) }
        disk.fileSystem.write(editor.data) { writeUtf8("not an image") }
        editor.commit()
        var requests = 0
        assertNotNull(owner("account").load(image, 240) { _, _ -> requests++; png(120, 60) })
        assertEquals(1, requests)
        assertNotNull(owner("account").load(image, 240) { _, _ -> error("Repaired entry must be readable") })
        Unit
    }

    @Test
    fun closingAnAccountRejectsItsLateCompletionBeforeDiskCommit() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val cache = owner("account")
        val load = async {
            cache.load(image, 240) { _, _ -> started.complete(Unit); finish.await(); png(120, 60) }
        }
        started.await()
        cache.close()
        finish.complete(Unit)
        assertFailsWith<CancellationException> { load.await() }
        assertNull(disk.openSnapshot(artworkCacheKey("account", image.url, 240)))
        assertNull(cache.peek(listOf(image), 240))
    }

    @Test
    fun keysAndRequestDiagnosticsDoNotContainUrlsOrCredentials() {
        val url = "https://images.test/private?api_key=fixture-secret"
        val key = artworkCacheKey("account-alias", url, 240)
        assertFalse(key.contains("images.test"))
        assertFalse(key.contains("fixture-secret"))
        assertFalse(key.contains("account-alias"))
        assertNotEquals(key, artworkCacheKey("another-account", url, 240))
        val data = ArtworkRequest(image.copy(url = url), 240) { _, _ -> null }
        assertEquals("ArtworkRequest(redacted)", data.toString())
    }

    @Test
    fun photoViewerKeepsItsOwnFullResolutionBudget() {
        val cache = ArtworkMemoryCache(PlatformContext.INSTANCE, maxWidthPx = 4096,
            maxSingleBytes = 64L * 1024 * 1024, maxCacheBytes = 96L * 1024 * 1024)
        owners += cache
        assertEquals(4096, cache.requestedWidth(4096))
        assertEquals(960, owner("account").requestedWidth(4096))
    }

    @Test
    fun anUnavailableDiskDirectoryDoesNotPreventNetworkImages() = runBlocking {
        val blocker = directory.root.toOkioPath() / "not-a-directory"
        FileSystem.SYSTEM.write(blocker) { writeUtf8("fixture") }
        val brokenDisk = DiskCache.Builder().directory(blocker / "images").maxSizeBytes(1024 * 1024).build()
        val cache = ArtworkMemoryCache(PlatformContext.INSTANCE, "account", brokenDisk)
        try {
            assertNotNull(cache.load(image, 240) { _, _ -> png(120, 60) })
        } finally {
            cache.close()
            brokenDisk.shutdown()
        }
        Unit
    }

    @Test
    fun fetcherReportsRealDiskHitsAndCachedBodyLimits() = runBlocking {
        val data = ArtworkRequest(image, 240) { _, _ -> png(120, 60) }
        val key = artworkCacheKey("account", image.url, 240)
        val loader = coil3.ImageLoader.Builder(PlatformContext.INSTANCE).diskCache(disk).build()
        val factory = ArtworkFetcher.Factory(4 * 1024 * 1024, { now }, {})
        val options = coil3.request.Options(PlatformContext.INSTANCE, diskCacheKey = key)
        try {
            val network = factory.create(data, options, loader).fetch() as coil3.fetch.SourceFetchResult
            assertEquals(DataSource.NETWORK, network.dataSource)
            network.source.close()
            val hit = factory.create(ArtworkRequest(image, 240) { _, _ -> error("Disk hit") }, options, loader)
                .fetch() as coil3.fetch.SourceFetchResult
            assertEquals(DataSource.DISK, hit.dataSource)
            hit.source.close()
            val limited = ArtworkFetcher.Factory(8, { now }, {})
            val small = limited.create(ArtworkRequest(image, 240) { _, _ -> byteArrayOf(1, 2, 3) }, options, loader)
                .fetch() as coil3.fetch.SourceFetchResult
            assertEquals(DataSource.NETWORK, small.dataSource)
            assertEquals(3, small.source.source().readByteArray().size)
            small.source.close()
        } finally { loader.shutdown() }
    }
}
