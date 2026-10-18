package cn.mafei.cmp.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerState
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

class MusicScreensTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private fun item(id: String, type: MediaType) = MediaItem(MediaId(id), ServerId("s"), ProviderKind.Jellyfin,
        if (type == MediaType.Audio) "歌曲$id" else "专辑$id", type, albumArtist = "歌手", runtimeMs = 180000)
    private val songs = listOf(item("一", MediaType.Audio), item("二", MediaType.Audio))
    private val art: @Composable (MediaItem, Modifier) -> Unit = { _, modifier -> Box(modifier.background(Color(0xFF446A85))) }

    @Test fun phoneLibraryTabsKeepTheOldSectionOrderAndSongsStartTheDisplayedQueue() {
        var tapped = -1
        rule.setContent {
            MaFeiTheme {
                var section by remember { mutableStateOf(MusicLibrarySection.Albums) }
                Box(Modifier.requiredSize(360.dp, 800.dp)) {
                    MusicLibraryScreen("音乐", section, MusicPageState(if (section == MusicLibrarySection.Tracks) songs
                        else listOf(item("一", MediaType.MusicAlbum)), initialized = true), mafeiLayout(360f, 800f),
                        onSection = { section = it }, onBack = {}, onRetry = {}, onMore = {}, onOpen = {},
                        onPlay = { tracks, index -> assertEquals(songs, tracks); tapped = index }, artwork = art)
                }
            }
        }
        rule.onNodeWithText("专辑").assertExists()
        rule.onNodeWithText("艺人").assertExists()
        rule.onNodeWithText("歌曲").performClick()
        rule.onNodeWithText("歌曲二").performClick()
        assertEquals(1, tapped)
        save("music-library-phone")
    }

    @Test fun phoneAlbumAndWideAlbumShareRowsAndPlayAll() = checkAlbum(360.dp, "music-album-phone")
    @Test fun wideAlbumUsesLegacySummaryAndTrackColumns() = checkAlbum(1000.dp, "music-album-wide")

    private fun checkAlbum(width: Dp, name: String) {
        var played = false
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.requiredSize(width, 800.dp)) {
                    MusicDetailScreen("专辑", false, MusicDetailState(item("一", MediaType.MusicAlbum)),
                        MusicPageState(songs, initialized = true), onBack = {}, onRetry = {}, onMoreTracks = {},
                        onMoreAlbums = {}, onAlbum = {},
                        onPlay = { tracks, index -> assertEquals(songs, tracks); assertEquals(0, index); played = true },
                        onFavorite = {}, artwork = art)
                }
            }
        }
        rule.onNodeWithText("歌曲一").assertIsDisplayed()
        rule.onNodeWithText("播放全部").performClick()
        assertTrue(played)
        save(name)
    }

    @Test fun artistShowsPopularTracksBeforeAlbums() {
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.requiredSize(900.dp, 800.dp)) {
                    MusicDetailScreen("歌手", true, MusicDetailState(item("一", MediaType.MusicArtist)),
                        MusicPageState(songs, initialized = true), MusicPageState(listOf(item("二", MediaType.MusicAlbum)), initialized = true),
                        onBack = {}, onRetry = {}, onMoreTracks = {}, onMoreAlbums = {}, onAlbum = {},
                        onPlay = { _, _ -> }, onFavorite = null, artwork = art)
                }
            }
        }
        val popular = rule.onNodeWithText("热门歌曲").fetchSemanticsNode().boundsInRoot
        val albums = rule.onNodeWithText("专辑").fetchSemanticsNode().boundsInRoot
        assertTrue(popular.top < albums.top)
    }

    @Test fun fullMusicPlayerPausesAndOpensTheRealQueue() {
        var paused = false
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.requiredSize(360.dp, 800.dp)) {
                    MusicPlayerScreen(songs.first(), PlayerState(phase = PlayerPhase.Playing, durationMs = 180000), false, null,
                        songs, 0, MusicRepeatMode.Off, {}, { paused = true }, {}, {}, null, {}, {}, {}, {}, false, {}, {}, art, art)
                }
            }
        }
        rule.onNodeWithContentDescription("暂停").performClick()
        assertTrue(paused)
        save("music-player-phone")
        rule.onNodeWithContentDescription("播放队列").performClick()
        rule.onNodeWithText("播放队列").assertIsDisplayed()
        rule.onNodeWithContentDescription("移除歌曲二").assertExists()
    }

    @Test fun libraryRefreshFailureKeepsSongsAndTheirPositionWhileFeedbackCanBeDismissed() {
        var page by mutableStateOf(MusicPageState(songs, initialized = true))
        var retries = 0
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(360.dp, 800.dp)) {
                MusicLibraryScreen("音乐", MusicLibrarySection.Tracks, page, mafeiLayout(360f, 800f),
                    onSection = {}, onBack = {}, onRetry = { retries++ }, onMore = {}, onOpen = {},
                    onPlay = { _, _ -> }, artwork = art)
            }
        } }
        val bounds = rule.onNodeWithText("歌曲一").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { page = page.copy(error = "刷新歌曲失败") }
        rule.onNodeWithTag("mafeiGlassConfirmCard").assertExists()
        rule.onNodeWithText("刷新歌曲失败").assertExists()
        assertEquals(bounds, rule.onNodeWithText("歌曲一").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("关闭").performClick()
        rule.onNodeWithTag("mafeiGlassConfirmCard").assertDoesNotExist()
        rule.runOnIdle { page = page.copy(loading = true) }
        rule.runOnIdle { page = page.copy(loading = false) }
        rule.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
        assertEquals("刷新歌曲失败", page.error)
    }

    @Test fun playerFailureDoesNotMoveTransportControlsAndRetryKeepsThePlayerMounted() {
        var failure by mutableStateOf<String?>(null)
        var retries = 0
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(360.dp, 800.dp)) {
                MusicPlayerScreen(songs.first(), PlayerState(phase = PlayerPhase.Playing, durationMs = 180000), false, failure,
                    songs, 0, MusicRepeatMode.Off, {}, {}, { retries++ }, {}, null, {}, {}, {}, {}, false, {}, {}, art, art)
            }
        } }
        val bounds = rule.onNodeWithContentDescription("暂停").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { failure = "播放准备失败" }
        rule.onNodeWithText("播放准备失败").assertExists()
        assertEquals(bounds, rule.onNodeWithContentDescription("暂停").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
        rule.onNodeWithContentDescription("播放队列").assertExists()
    }

    @Test fun libraryPaginationRetryKeepsLoadedSongsAndContinuesTheFailedPage() {
        var page by mutableStateOf(MusicPageState(songs, cursor = PageCursor("next"), initialized = true, error = "下一页读取失败"))
        var refreshed = 0
        var continued = 0
        val next = item("三", MediaType.Audio)
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(360.dp, 800.dp)) {
                MusicLibraryScreen("音乐", MusicLibrarySection.Tracks, page, mafeiLayout(360f, 800f),
                    onSection = {}, onBack = {},
                    onRetry = { refreshed++; page = MusicPageState(listOf(songs.first()), initialized = true) },
                    onMore = { continued++; page = MusicPageState(songs + next, initialized = true) },
                    onOpen = {}, onPlay = { _, _ -> }, artwork = art)
            }
        } }
        rule.onNodeWithText("重试").performClick()
        assertEquals(0, refreshed)
        assertEquals(1, continued)
        rule.onNodeWithText("歌曲二").assertExists()
        rule.onNodeWithText("歌曲三").assertExists()
    }

    @Test fun albumTrackPaginationRetryDoesNotRefreshTheWholeDetail() = checkDetailPagination(artist = false)
    @Test fun artistAlbumPaginationRetryDoesNotRefreshTheWholeDetail() = checkDetailPagination(artist = true)

    private fun checkDetailPagination(artist: Boolean) {
        var tracks by mutableStateOf(MusicPageState(songs, initialized = true,
            cursor = if (artist) null else PageCursor("tracks-next"), error = if (artist) null else "歌曲下一页读取失败"))
        var albums by mutableStateOf(MusicPageState(listOf(item("二", MediaType.MusicAlbum)), initialized = true,
            cursor = if (artist) PageCursor("albums-next") else null, error = if (artist) "专辑下一页读取失败" else null))
        var refreshed = 0
        var tracksContinued = 0
        var albumsContinued = 0
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(900.dp, 800.dp)) {
                MusicDetailScreen("音乐详情", artist,
                    MusicDetailState(item("一", if (artist) MediaType.MusicArtist else MediaType.MusicAlbum)),
                    tracks, albums, onBack = {}, onRetry = { refreshed++ },
                    onMoreTracks = { tracksContinued++; tracks = tracks.copy(cursor = null, error = null) },
                    onMoreAlbums = { albumsContinued++; albums = albums.copy(cursor = null, error = null) },
                    onAlbum = {}, onPlay = { _, _ -> }, onFavorite = null, artwork = art)
            }
        } }
        rule.onNodeWithText("重试").performClick()
        assertEquals(0, refreshed)
        assertEquals(if (artist) 0 else 1, tracksContinued)
        assertEquals(if (artist) 1 else 0, albumsContinued)
        assertEquals(songs, tracks.items)
        assertEquals("二", albums.items.single().id.value)
    }

    private fun save(name: String) {
        val bitmap = rule.onRoot().captureToImage()
        val pixels = bitmap.toPixelMap()
        val output = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val color = pixels[x, y]
            output.setRGB(x, y, (color.alpha * 255).toInt().shl(24) or (color.red * 255).toInt().shl(16)
                or (color.green * 255).toInt().shl(8) or (color.blue * 255).toInt())
        }
        val folder = File("build/reports/music-ui").also { it.mkdirs() }
        ImageIO.write(output, "png", File(folder, "$name.png"))
    }
}
