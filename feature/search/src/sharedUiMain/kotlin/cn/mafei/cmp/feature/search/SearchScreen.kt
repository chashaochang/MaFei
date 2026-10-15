package cn.mafei.cmp.feature.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.isPhoneNavigation
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyEmptyView
import cn.mafei.cmp.common.ui.LegacyFailedView
import cn.mafei.cmp.common.ui.LegacyVideoCard
import cn.mafei.cmp.common.ui.mediaArtworkGridHoverInset
import cn.mafei.cmp.common.ui.mediaArtworkHoverSideInset
import cn.mafei.cmp.common.ui.mafeiArtworkShelfBleed
import cn.mafei.cmp.common.ui.MaFeiActionBar
import cn.mafei.cmp.common.ui.mafeiPageTitleBarContentHeight
import cn.mafei.cmp.common.ui.MaFeiGlassConfirmDialog
import cn.mafei.cmp.common.ui.MaFeiLegacyDrawables
import cn.mafei.cmp.common.ui.MaFeiLoadingIndicator
import cn.mafei.cmp.common.ui.MaFeiLoadingStyle
import cn.mafei.cmp.common.ui.legacyMediaColumns
import cn.mafei.cmp.common.ui.mafeiInteractive
import cn.mafei.cmp.core.domain.MediaItem
import org.jetbrains.compose.resources.painterResource

/** Mechanical Compose translation of ArkUI `SearchPage`. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    query: String,
    results: List<MediaItem>,
    histories: List<String>,
    loading: Boolean,
    error: String?,
    layout: MaFeiLayout,
    gridState: LazyGridState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRecordHistory: (String) -> Unit,
    onClearHistories: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onPlay: ((MediaItem) -> Unit)? = null,
    onToggleWatched: ((MediaItem) -> Unit)? = null,
    onToggleFavorite: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    artwork: (@Composable (MediaItem, Modifier) -> Unit)? = null,
) {
    val safeArea = LocalMaFeiSafeArea.current
    var text by remember { mutableStateOf(query) }
    var showClearDialog by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val backdrop = rememberGlassBackdrop()
    val actionBarBackdrop = rememberGlassBackdrop()

    val pageHorizontalPadding = if (layout.gridWidthDp < 600f) 16.dp else 24.dp
    val columns = legacyMediaColumns(layout.gridWidthDp)
    val phoneNavigation = layout.isPhoneNavigation
    val titleBarHeight = mafeiPageTitleBarContentHeight()
    // On wide windows the input is content below the page title, not its replacement.
    val contentTop = safeArea.top + titleBarHeight + if (phoneNavigation) 16.dp else 72.dp
    val searchField: @Composable (GlassBackdrop) -> Unit = { fieldBackdrop ->
        SearchField(
            value = text,
            onValueChange = { value ->
                text = value
                onQueryChange(value)
            },
            onSubmit = {
                val normalized = text.trim()
                if (normalized.isNotEmpty()) {
                    keyboard?.hide()
                    onSubmit(normalized)
                }
            },
            focusRequester = focusRequester,
            backdrop = fieldBackdrop,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().captureGlassBackdrop(actionBarBackdrop),
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .captureGlassBackdrop(backdrop),
            )
            Column(
                Modifier.fillMaxSize()
                    .padding(top = contentTop),
            ) {
                val normalized = text.trim()
                if (normalized.isEmpty()) {
                    if (histories.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(
                                start = safeArea.start + pageHorizontalPadding,
                                end = safeArea.end + pageHorizontalPadding,
                                bottom = safeArea.bottom,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().height(56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "搜索历史",
                                    Modifier.weight(1f).semantics { heading() },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "清除",
                                    Modifier.mafeiInteractive(
                                        { showClearDialog = true },
                                        radiusDp = 18f,
                                        label = "清除搜索历史",
                                    ).padding(horizontal = 8.dp, vertical = 9.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                histories.forEach { history ->
                                    GlassSurface(
                                        backdrop = backdrop,
                                        spec = GlassSurfaceSpec(
                                            role = GlassRole.Overlay,
                                            level = GlassLevel.Reduced,
                                            tintAlpha = .18f,
                                            intensity = .72f,
                                            cornerRadiusDp = 18f,
                                        ),
                                        tint = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.height(36.dp).mafeiInteractive(
                                            onClick = {
                                                text = history
                                                onQueryChange(history)
                                            },
                                            radiusDp = 18f,
                                            label = history,
                                        ),
                                    ) {
                                        Box(
                                            Modifier.padding(horizontal = 14.dp).height(36.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                history,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else when {
                    loading && results.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        MaFeiLoadingIndicator(
                            modifier = Modifier.size(40.dp),
                            style = MaFeiLoadingStyle.Page,
                        )
                    }
                    error != null && results.isEmpty() -> LegacyFailedView(
                        onRetry = { onSubmit(normalized) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> Column(
                        Modifier.fillMaxSize().padding(
                            start = safeArea.start + pageHorizontalPadding,
                            end = safeArea.end + pageHorizontalPadding,
                        ),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().height(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "搜索结果",
                                Modifier.semantics { heading() },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "${results.size} 个",
                                Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                        if (results.isEmpty()) {
                            LegacyEmptyView(modifier = Modifier.weight(1f))
                        } else {
                            val gap = if (layout.gridWidthDp < 320f) 12.dp else 16.dp
                            val hoverInset = mediaArtworkGridHoverInset(layout.gridWidthDp.dp, columns, gap)
                            val sideInset = mediaArtworkHoverSideInset((layout.gridWidthDp.dp - gap * (columns - 1)) / columns)
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                state = gridState,
                                modifier = Modifier.weight(1f).mafeiArtworkShelfBleed(sideInset),
                                contentPadding = PaddingValues(
                                    start = sideInset, end = sideInset, top = hoverInset,
                                    bottom = safeArea.bottom + 30.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                                verticalArrangement = Arrangement.spacedBy(
                                    maxOf(20.dp, hoverInset),
                                ),
                            ) {
                                items(results, key = { it.id.value }) { item ->
                                    LegacyVideoCard(
                                        item = item,
                                        onClick = {
                                            onRecordHistory(normalized)
                                            onOpen(item)
                                        },
                                        onPlay = onPlay?.let { play -> { play(item) } },
                                        onToggleWatched = onToggleWatched?.let { toggle -> { toggle(item) } },
                                        onToggleFavorite = onToggleFavorite?.let { toggle -> { toggle(item) } },
                                        poster = artwork?.let { draw ->
                                            { cardModifier -> draw(item, cardModifier) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        MaFeiActionBar(
            title = "搜索",
            // Search is a primary shell action on large windows: the shell's
            // expand control owns navigation there. Phone search remains a
            // full-screen page and keeps the normal back button.
            onBack = onBack.takeIf { phoneNavigation },
            backdrop = actionBarBackdrop,
            centerTitleOnScreen = !phoneNavigation,
            titleContent = if (phoneNavigation) {
                { buttonBackdrop -> searchField(buttonBackdrop) }
            } else null,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        if (!phoneNavigation) {
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(
                    top = safeArea.top + titleBarHeight + 8.dp,
                    start = safeArea.start + pageHorizontalPadding,
                    end = safeArea.end + pageHorizontalPadding,
                ),
            ) {
                searchField(actionBarBackdrop)
            }
        }
    }

    if (showClearDialog) {
        MaFeiGlassConfirmDialog(
            title = "删除确认",
            content = "确定删除历史记录吗?",
            dismissText = "取消",
            confirmText = "删除",
            destructive = true,
            backdrop = backdrop,
            onDismiss = { showClearDialog = false },
            onConfirm = {
                showClearDialog = false
                onClearHistories()
            },
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        backdrop = backdrop,
        spec = GlassSurfaceSpec(
            role = GlassRole.Overlay,
            level = GlassLevel.Full,
            tintAlpha = .22f,
            intensity = .92f,
            cornerRadiusDp = 24f,
        ),
        tint = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().height(48.dp).testTag("searchInput"),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                Image(
                    painter = painterResource(MaFeiLegacyDrawables.search),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isBlank()) {
                                Text(
                                    "搜索影片、剧集和视频",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            field()
                        }
                    },
                )
                if (value.isNotEmpty()) {
                    Image(
                        painter = painterResource(MaFeiLegacyDrawables.close),
                        contentDescription = "清除搜索内容",
                        modifier = Modifier.size(28.dp).clickable { onValueChange("") }.padding(5.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
                Text(
                    "搜索",
                    Modifier.height(48.dp).mafeiInteractive(onSubmit, radiusDp = 0f, label = "搜索")
                        .padding(start = 8.dp, end = 2.dp, top = 13.dp, bottom = 13.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
        }
    }
}
