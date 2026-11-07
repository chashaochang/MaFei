package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.components.LiquidActionBar
import cn.mafei.cmp.common.glass.currentGlassStyle
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.LegacyEmptyView
import cn.mafei.cmp.common.ui.MaFeiActionBarButton
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.session.WebPageRequest

internal class EmbeddedWebCallbacks(
    val onNativeAction: (String, String) -> Unit,
    val onBack: () -> Unit,
    val registerBack: ((() -> Boolean)?) -> Unit,
    val onFailure: () -> Unit = {},
    val registerRefresh: ((() -> Unit)?) -> Unit = {},
    val onTitleChanged: (String) -> Unit = {},
    val onProgressChanged: (Int) -> Unit = {},
)

internal val LocalEmbeddedWebHost = staticCompositionLocalOf<EmbeddedWebHost> { UnsupportedEmbeddedWebHost }

internal class EmbeddedWebPageState {
    var request: WebPageRequest? = null
    var location: String? = null
}

/** Platform renderer only; navigation, title, controls and playback queue stay in CMP. */
internal interface EmbeddedWebHost {
    val nativePlaybackSupported: Boolean get() = false
    @Composable fun Content(request: WebPageRequest, callbacks: EmbeddedWebCallbacks, modifier: Modifier)
}

internal object UnsupportedEmbeddedWebHost : EmbeddedWebHost {
    @Composable override fun Content(request: WebPageRequest, callbacks: EmbeddedWebCallbacks, modifier: Modifier) {
        LegacyEmptyView(title = "此平台暂不支持内嵌浏览", modifier = modifier)
    }
}

/**
 * Jellyfin Web is hosted full-screen. Its legacy bottom control strip remains
 * available over the page, with an explicit collapse affordance for small screens.
 */
@Composable
internal fun EmbeddedWebPage(
    host: EmbeddedWebHost,
    request: WebPageRequest?,
    title: String,
    error: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNativeAction: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var browserBack by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var browserRefresh by remember { mutableStateOf<(() -> Unit)?>(null) }
    var failed by remember(request) { mutableStateOf(false) }
    var attempt by remember(request) { mutableIntStateOf(0) }
    var pageTitle by remember(request, title) { mutableStateOf(title) }
    var progress by remember(request) { mutableIntStateOf(0) }
    var controlsExpanded by remember(request) { mutableStateOf(true) }
    val safeArea = LocalMaFeiSafeArea.current
    val backdrop = rememberGlassBackdrop()
    val selectedGlassStyle = currentGlassStyle()
    val webControlsGlassStyle = if (selectedGlassStyle == GlassStyle.Liquid) {
        GlassStyle.GaussianBlur
    } else {
        selectedGlassStyle
    }
    val back = { if (browserBack?.invoke() != true) onBack() }

    cn.mafei.cmp.common.ui.MaFeiBackHandler(enabled = true) { back() }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .captureGlassBackdrop(backdrop),
        ) {
            Spacer(Modifier.height(safeArea.top))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (request == null) {
                    if (error != null) Column(Modifier.fillMaxSize()) {
                        LegacyEmptyView(title = error, modifier = Modifier.weight(1f))
                        cn.mafei.cmp.common.ui.LegacyListGroup(
                            listOf(cn.mafei.cmp.common.ui.LegacyListGroupItem("重试")),
                            onItemClick = { onRetry() },
                            modifier = Modifier.padding(24.dp),
                        )
                    } else cn.mafei.cmp.common.ui.MaFeiLoadingIndicator(Modifier.fillMaxSize())
                } else if (failed) {
                    Column(Modifier.fillMaxSize()) {
                        LegacyEmptyView(title = "网页加载失败", modifier = Modifier.weight(1f))
                        cn.mafei.cmp.common.ui.LegacyListGroup(
                            listOf(cn.mafei.cmp.common.ui.LegacyListGroupItem("重试")),
                            onItemClick = { _ -> failed = false; progress = 0; attempt++ },
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else key(request, attempt) {
                    val latestAction by rememberUpdatedState(onNativeAction)
                    val latestBack by rememberUpdatedState(onBack)
                    val callbacks = remember(request) {
                        EmbeddedWebCallbacks(
                            onNativeAction = { action, value -> latestAction(action, value) },
                            onBack = { latestBack() },
                            registerBack = { browserBack = it },
                            onFailure = { failed = true },
                            registerRefresh = { browserRefresh = it },
                            onTitleChanged = { next -> if (next.isNotBlank()) pageTitle = next },
                            onProgressChanged = { next -> progress = next.coerceIn(0, 100) },
                        )
                    }
                    host.Content(request, callbacks, Modifier.fillMaxSize())
                }
            }
        }

        if (request != null && !failed && progress < 100) {
            Box(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = safeArea.top)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress / 100f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        GlassStyleScope(webControlsGlassStyle) {
            LiquidActionBar(
                backdrop = backdrop,
                topInset = 0.dp,
                startInset = safeArea.start,
                endInset = safeArea.end,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = safeArea.bottom),
                actionCount = if (controlsExpanded) 3 else 1,
                // Keep the legacy independent floating controls; the full-width
                // action-bar gradient is not part of the WebEasyTong surface.
                materialAlpha = 0f,
                navigation = if (controlsExpanded) ({ buttonBackdrop ->
                    MaFeiActionBarButton(
                        onClick = back,
                        backdrop = buttonBackdrop,
                        contentDescription = "网页后退",
                    ) { MaFeiIcon(MaFeiGlyph.Back, color = MaterialTheme.colorScheme.onSurface) }
                }) else null,
                title = {
                    if (controlsExpanded) Text(
                        pageTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                centerTitleOnScreen = false,
                actions = { buttonBackdrop ->
                    if (controlsExpanded) {
                        MaFeiActionBarButton(
                            onClick = onBack,
                            backdrop = buttonBackdrop,
                            contentDescription = "关闭网页",
                        ) { MaFeiIcon(MaFeiGlyph.Close, color = MaterialTheme.colorScheme.onSurface) }
                        MaFeiActionBarButton(
                            onClick = { browserRefresh?.invoke(); progress = 0 },
                            backdrop = buttonBackdrop,
                            contentDescription = "刷新网页",
                        ) { MaFeiIcon(MaFeiGlyph.Refresh, color = MaterialTheme.colorScheme.onSurface) }
                    }
                    MaFeiActionBarButton(
                        onClick = { controlsExpanded = !controlsExpanded },
                        backdrop = buttonBackdrop,
                        contentDescription = if (controlsExpanded) "收起网页工具栏" else "展开网页工具栏",
                    ) {
                        MaFeiIcon(
                            MaFeiGlyph.Chevron,
                            modifier = Modifier.rotate(if (controlsExpanded) 180f else 0f),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun JellyfinEmbeddedPage(
    session: cn.mafei.cmp.session.ApplicationSession,
    memory: EmbeddedWebPageState,
    onBack: () -> Unit,
    onNativeAction: (String, String) -> Unit,
) {
    var request by remember(session) { mutableStateOf(memory.request?.let { page ->
        memory.location?.takeIf(page::accepts)?.let(page::atUrl) ?: page
    }) }
    var error by remember(session) { mutableStateOf<String?>(null) }
    var revision by remember(session) { mutableIntStateOf(0) }
    LaunchedEffect(session, revision) {
        error = null
        try {
            if (request == null || revision > 0) {
                val page = session.binding.webPage() ?: error("Web not supported")
                request = memory.location?.takeIf(page::accepts)?.let(page::atUrl) ?: page
                memory.request = page
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "网页加载失败"
        }
    }
    EmbeddedWebPage(
        LocalEmbeddedWebHost.current,
        request,
        "网(Web)易通",
        error,
        onBack,
        { revision++ },
        { action, value ->
            if (action == "location") {
                if (request?.accepts(value) == true) memory.location = value
            } else onNativeAction(action, value)
        },
        Modifier.fillMaxSize(),
    )
}
