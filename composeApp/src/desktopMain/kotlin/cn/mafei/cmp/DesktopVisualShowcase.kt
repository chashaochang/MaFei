package cn.mafei.cmp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.MaFeiWindowEnvironment
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.mafeiAdaptiveWindow
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.common.glass.GlassRenderingEnvironment
import cn.mafei.cmp.common.glass.rememberGlassRenderHealth
import cn.mafei.cmp.common.ui.MaFeiAdaptiveShell
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.common.ui.MaFeiNavItem
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaCollection
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.MediaUserData
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.feature.home.HomeScreen
import cn.mafei.cmp.player.api.PlayerPhase
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerState
import cn.mafei.cmp.player.ui.LiquidMiniPlayer
import cn.mafei.cmp.player.ui.MiniPlayerMedia
import cn.mafei.cmp.player.ui.NowPlayingMedia
import cn.mafei.cmp.player.ui.NowPlayingScreen
import cn.mafei.cmp.player.ui.toMiniPlayerPresentation

/** Deterministic Desktop-only entry used for visual acceptance without a server session. */
@Composable
internal fun DesktopVisualShowcase() {
    var dark by remember {
        mutableStateOf(System.getProperty("mafei.desktop.showcaseDark") != "false")
    }
    var destination by remember { mutableStateOf("home") }
    var playing by remember { mutableStateOf(true) }
    var playerExpanded by remember {
        mutableStateOf(System.getProperty("mafei.desktop.showcasePlayer") == "true")
    }
    var playerPositionMs by remember { mutableStateOf(37L * 60_000L) }
    val health = rememberGlassRenderHealth()
    val gridState = rememberLazyGridState()
    val chromeCompactProgress by remember(gridState) {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) 1f
            else (gridState.firstVisibleItemScrollOffset / 160f).coerceIn(0f, 1f)
        }
    }
    val collections = remember { showcaseCollections() }
    val items = remember { showcaseItems() }

    MaFeiTheme(dark) {
        GlassRenderingEnvironment(health) {
            Surface(color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val windowFeatures = LocalMaFeiWindowFeatures.current
                    val layout = remember(maxWidth, maxHeight, density.fontScale, windowFeatures) {
                        mafeiLayout(
                            mafeiAdaptiveWindow(
                                MaFeiWindowEnvironment(
                                    widthDp = maxWidth.value,
                                    heightDp = maxHeight.value,
                                    fontScale = density.fontScale,
                                    occlusions = windowFeatures.occlusions,
                                    foldPosture = windowFeatures.foldPosture,
                                    displayRole = windowFeatures.displayRole,
                                ),
                            ),
                        )
                    }
                    val destinations = remember {
                        listOf(
                            MaFeiNavItem("home", "首页", MaFeiGlyph.Home),
                            MaFeiNavItem("library", "媒体库", MaFeiGlyph.Library),
                        )
                    }
                    val playerState = PlayerState(
                        phase = if (playing) PlayerPhase.Playing else PlayerPhase.Paused,
                        resourceId = items.first().id.value,
                        positionMs = playerPositionMs,
                        bufferedPositionMs = playerPositionMs + 8L * 60_000L,
                        durationMs = items.first().runtimeMs,
                    )
                    if (playerExpanded) {
                        NowPlayingScreen(
                            media = NowPlayingMedia(
                                id = items.first().id.value,
                                title = items.first().title,
                                subtitle = "2026 · 电影 · 视觉验收",
                                description = items.first().overview,
                            ),
                            state = playerState,
                            capabilities = PlayerCapabilities(),
                            onBack = { playerExpanded = false },
                            onTogglePlay = { playing = !playing },
                            onRetry = { playing = true },
                            onSeek = { playerPositionMs = it },
                            artwork = { modifier -> ShowcaseArtwork(items.first(), modifier, false) },
                            backdropArtwork = { modifier -> ShowcaseArtwork(items.first(), modifier, true) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else MaFeiAdaptiveShell(
                        layout = layout,
                        destinations = destinations,
                        selectedDestination = destination,
                        onDestination = { destination = it },
                        libraries = collections.map {
                            MaFeiNavItem(it.id.value, it.name, MaFeiGlyph.Library)
                        },
                        selectedLibrary = collections.first().id.value,
                        onLibrary = { destination = "library" },
                        bottomNavigationCompactProgress = { chromeCompactProgress },
                        accountLabel = "视觉验收账号\n本地固定数据",
                        background = { ShowcaseAmbientBackground() },
                        accountActions = {
                            IconButton(onClick = { dark = !dark }) {
                                MaFeiIcon(MaFeiGlyph.Account, Modifier.size(20.dp))
                            }
                        },
                        scene = {
                            HomeScreen(
                                collections = collections,
                                items = items,
                                selectedCollection = collections.first().id,
                                loading = false,
                                error = null,
                                stale = false,
                                canLoadMore = false,
                                overview = destination == "home",
                                layout = layout,
                                onCollection = {},
                                onItem = {},
                                onPlay = {
                                    playing = true
                                    playerExpanded = true
                                },
                                onRetry = {},
                                onLoadMore = {},
                                onOpenLibrary = { destination = "library" },
                                heroAutoplay = false,
                                accountActions = {
                                    IconButton(onClick = { dark = !dark }) {
                                        MaFeiIcon(MaFeiGlyph.Account, Modifier.size(20.dp))
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                gridState = gridState,
                                artwork = { item, modifier -> ShowcaseArtwork(item, modifier, false) },
                                backdropArtwork = { item, modifier -> ShowcaseArtwork(item, modifier, true) },
                            )
                        },
                        overlay = { backdrop ->
                            LiquidMiniPlayer(
                                media = MiniPlayerMedia(
                                    id = items.first().id.value,
                                    title = items.first().title,
                                    subtitle = "正在播放 · 视觉验收",
                                ),
                                presentation = playerState.toMiniPlayerPresentation(),
                                backdrop = backdrop,
                                onOpen = { playerExpanded = true },
                                onTogglePlay = { playing = !playing },
                                compactProgress = { chromeCompactProgress },
                                artwork = { modifier -> ShowcaseArtwork(items.first(), modifier, false) },
                                modifier = Modifier
                                    .align(
                                        if (layout.navigation == cn.mafei.cmp.common.design_system.NavigationPlacement.Bottom) {
                                            Alignment.BottomCenter
                                        } else {
                                            Alignment.BottomEnd
                                        },
                                    )
                                    .padding(
                                        start = if (layout.navigation == cn.mafei.cmp.common.design_system.NavigationPlacement.Bottom) 16.dp else 24.dp,
                                        end = 16.dp,
                                        bottom = if (layout.navigation == cn.mafei.cmp.common.design_system.NavigationPlacement.Bottom) 88.dp else 24.dp,
                                    )
                                    .widthIn(max = 430.dp)
                                    .fillMaxWidth(),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShowcaseAmbientBackground() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = .28f),
                    MaterialTheme.colorScheme.background,
                    Color(0xFF5F426D).copy(alpha = .26f),
                ),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(11) { index ->
                val x = size.width * index / 10f
                drawLine(
                    color = Color.White.copy(alpha = if (index % 2 == 0) .09f else .04f),
                    start = Offset(x - 120.dp.toPx(), 0f),
                    end = Offset(x + 180.dp.toPx(), size.height),
                    strokeWidth = 22.dp.toPx(),
                )
            }
        }
    }
}

@Composable
private fun ShowcaseArtwork(item: MediaItem, modifier: Modifier, landscape: Boolean) {
    val palette = showcasePalette(item.id.value)
    Box(modifier.background(Brush.linearGradient(palette))) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * if (landscape) .46f else .7f
            drawCircle(
                color = Color.White.copy(alpha = .16f),
                radius = radius,
                center = Offset(size.width * .78f, size.height * .22f),
            )
            drawCircle(
                color = Color.Black.copy(alpha = .18f),
                radius = radius * .72f,
                center = Offset(size.width * .18f, size.height * .82f),
            )
        }
    }
}

private fun showcasePalette(id: String): List<Color> = when (id.takeLast(1)) {
    "1", "5" -> listOf(Color(0xFF15395B), Color(0xFF4A7D92), Color(0xFFD19474))
    "2", "6" -> listOf(Color(0xFF251B43), Color(0xFF765C91), Color(0xFFD68A7D))
    "3", "7" -> listOf(Color(0xFF123B3C), Color(0xFF447C6E), Color(0xFFD3A35F))
    else -> listOf(Color(0xFF3E2431), Color(0xFF8C4C58), Color(0xFFDAA475))
}

private fun showcaseCollections(): List<MediaCollection> {
    val server = ServerId("showcase-server")
    return listOf(
        MediaCollection(CollectionId("movies"), server, ProviderKind.Jellyfin, "电影", setOf(MediaType.Movie)),
        MediaCollection(CollectionId("series"), server, ProviderKind.Jellyfin, "剧集", setOf(MediaType.Series)),
    )
}

private fun showcaseItems(): List<MediaItem> {
    val server = ServerId("showcase-server")
    val titles = listOf("群星之间", "漫长的季节", "海岸来信", "昨日回声", "冬日列车", "黎明之后", "无声航线", "南方剧场")
    return titles.mapIndexed { index, title ->
        MediaItem(
            id = MediaId("showcase-${index + 1}"),
            serverId = server,
            provider = ProviderKind.Jellyfin,
            title = title,
            mediaType = when {
                index in 3..5 -> MediaType.Episode
                index % 3 == 1 -> MediaType.Series
                else -> MediaType.Movie
            },
            overview = "在熟悉与未知之间，一段关于选择、陪伴和远方的故事缓缓展开。",
            collectionId = CollectionId(if (index % 3 == 1) "series" else "movies"),
            year = 2026 - index,
            dateAddedEpochMs = 10_000L - index,
            runtimeMs = (92L + index * 7L) * 60_000L,
            genres = listOf("剧情", if (index % 2 == 0) "科幻" else "悬疑"),
            userData = when (index) {
                0 -> MediaUserData(playbackPositionMs = 37L * 60_000L)
                1 -> MediaUserData(playbackPositionMs = 18L * 60_000L)
                2 -> MediaUserData(isFavorite = true)
                4 -> MediaUserData(isPlayed = true)
                else -> MediaUserData()
            },
            canPlay = true,
        )
    }
}
