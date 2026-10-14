package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import cn.mafei.cmp.common.design_system.MaFeiLayout
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.design_system.LocalMaFeiSafeArea
import cn.mafei.cmp.common.design_system.MaFeiSafeArea
import cn.mafei.cmp.common.glass.GlassBackdrop
import cn.mafei.cmp.common.glass.GlassSurface
import cn.mafei.cmp.common.glass.components.LiquidButton
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassMaterial
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import cn.mafei.cmp.common.design_system.LocalMaFeiPopupColors
import cn.mafei.cmp.common.design_system.MaFeiPopupReferenceColors
import cn.mafei.cmp.common.ui.MaFeiGlyph
import cn.mafei.cmp.common.ui.mafeiDismissOnOutsidePointer
import cn.mafei.cmp.common.ui.MaFeiIcon
import cn.mafei.cmp.session.*
import androidx.navigation3.ui.NavDisplay

/**
 * 小屏（手机）上的「我的」容器：全屏铺开、非模态，看起来和普通页面跳转一样。
 *
 * 它画在场景里（`scene` 槽，在浏览页之后），所以外壳的底部标签栏是它的兄弟节点：标签栏
 * 照样折射这一页的内容，不会变成一块压在上面的"死"浮层。铺一层窗口底色是为了盖住下面
 * 仍然活着的浏览页（NavDisplay 换了路由是先画后画、不是替换，所以底下那页还在）。
 */
@Composable
internal fun MineFullScreenHost(
    content: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content(Modifier.fillMaxSize())
    }
}

/**
 * Modal account surface, modelled on the macOS/iPadOS account popover.
 *
 * It is a sibling of the scene rather than a platform Dialog, so the glass panel
 * samples the real page underneath, the scrim dims the whole window (sidebar
 * included) and dismissing restores the untouched route. Escape, the scrim and
 * the explicit close control all dismiss it; the panel swallows its own taps so
 * pressing inside never falls through to the scrim.
 */
@Composable
internal fun MineAccountSheet(
    layout: MaFeiLayout,
    backdrop: GlassBackdrop,
    onDismiss: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val largeWindow = layout.navigation != NavigationPlacement.Bottom
        val sheetWidth = if (largeWindow) {
            // Keep the modal visually subordinate to the page behind it. The
            // previous large-screen width is the baseline, then the sheet is
            // reduced to roughly two thirds of that width.
            ((maxWidth - 48.dp).coerceIn(280.dp, 720.dp) * (2f / 3f))
                .coerceAtLeast(280.dp)
        } else {
            maxWidth
        }
        val sheetHeight = (maxHeight * .74f).coerceAtLeast(320.dp)
        val topBarHeight = if (largeWindow) MineSheetLargeTopBarHeight else MineSheetTopBarHeight
        val closeButtonTopInset = (topBarHeight - 44.dp) / 2f
        val closeButtonEndInset = if (largeWindow) 16.dp else 8.dp
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            runCatching { focusRequester.requestFocus() }
        }
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
        ) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = .46f))
                    .mafeiDismissOnOutsidePointer(onDismiss),
            )
            Box(
                Modifier
                    .align(if (largeWindow) Alignment.Center else Alignment.BottomCenter)
                    .width(sheetWidth)
                    .height(sheetHeight)
                    .clip(RoundedCornerShape(MineSheetCornerDp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .semantics { contentDescription = "我的" },
            ) {
                val sheetBackdrop = rememberGlassBackdrop()
                val closeButtonBackdrop = sheetBackdrop
                GlassSurface(
                    backdrop = backdrop,
                    spec = MineAccountSheetSurfaceSpec,
                    // The modal is a canvas; its list groups/cards use surface.
                    // Using surface here as well made every item disappear into
                    // the popup body.
                    tint = MaFeiPopupReferenceColors.container,
                    modifier = Modifier.fillMaxSize(),
                    exportedBackdrop = sheetBackdrop,
                ) {
                    CompositionLocalProvider(
                        LocalMaFeiSafeArea provides MaFeiSafeArea(),
                        LocalMaFeiPopupColors provides MaFeiPopupReferenceColors,
                        // MineScreen is also rendered as a window-level dark
                        // surface. Keep its loading/empty labels in the same
                        // light ink hierarchy as the rest of the popup.
                        LocalContentColor provides Color.White,
                    ) {
                        content(
                            Modifier.fillMaxSize().padding(top = topBarHeight)
                                .testTag("mineAccountSheetContent"),
                        )
                    }
                }
                // 移动端保留底部弹窗拖拽指示器；大屏居中弹窗只保留标题和关闭按钮。
                Box(
                    Modifier.fillMaxWidth().height(topBarHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!largeWindow) {
                        Box(
                            Modifier.align(Alignment.TopCenter).padding(top = 8.dp).width(36.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .36f)),
                        )
                    }
                    Text(
                        "我的",
                        Modifier.fillMaxWidth().padding(horizontal = 52.dp),
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    )
                }
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = closeButtonBackdrop,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = closeButtonTopInset, end = closeButtonEndInset)
                        .size(44.dp)
                        .semantics { contentDescription = "关闭我的" },
                    height = 44.dp,
                    horizontalPadding = 0.dp,
                ) {
                    MaFeiIcon(
                        MaFeiGlyph.Close,
                        Modifier.size(20.dp),
                        Color.White,
                    )
                }
            }
        }
    }
}

/** 弹窗顶部导航区（关闭按钮所在条带）的高度：所有子页面内容在此之下。 */
private val MineSheetTopBarHeight = 56.dp
// Keep the 12dp inset used by the reference while centering the 44dp control:
// (68 - 44) / 2 = 12dp on both sides.
private val MineSheetLargeTopBarHeight = 68.dp
private const val MineSheetCornerDp = 24f
private val MineAccountSheetSurfaceSpec = GlassSurfaceSpec(
    role = GlassRole.Dialog,
    level = GlassLevel.Opaque,
    tintAlpha = 1f,
    intensity = 0f,
    cornerRadiusDp = MineSheetCornerDp,
    material = GlassMaterial.Frosted,
)
