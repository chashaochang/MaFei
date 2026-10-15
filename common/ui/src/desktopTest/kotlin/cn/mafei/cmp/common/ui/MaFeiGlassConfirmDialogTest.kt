package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.common.glass.GlassStyleScope
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** The confirmation owns dismissal, so only the pressed control may fire. */
class MaFeiGlassConfirmDialogTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private var confirmed = 0
    private var dismissed = 0

    private fun showDialog() {
        composeRule.setContent {
            MaterialTheme {
                val backdrop = rememberGlassBackdrop()
                Surface(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) { Text("scene") }
                MaFeiGlassConfirmDialog(
                    title = "删除账号",
                    content = "确定要删除「xiaobai」这个账号吗？",
                    confirmText = "删除",
                    dismissText = "取消",
                    destructive = true,
                    backdrop = backdrop,
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun showsTheRequestAndDispatchesOnlyThePressedAction() {
        showDialog()
        composeRule.onNodeWithText("删除账号").assertIsDisplayed()
        composeRule.onNodeWithText("确定要删除「xiaobai」这个账号吗？").assertIsDisplayed()

        // The panel swallows its own taps: pressing inside never dismisses.
        composeRule.onNodeWithTag("mafeiGlassConfirmCard").performClick()
        assertEquals(0, dismissed)
        assertEquals(0, confirmed)

        composeRule.onNodeWithText("取消").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)

        composeRule.onNodeWithText("删除").performClick()
        assertEquals(1, confirmed)
        assertEquals(1, dismissed)
    }

    @Test
    fun tappingOutsideThePanelDismissesWithoutConfirming() {
        showDialog()
        // A corner of the scrim, never the centred panel.
        composeRule.onNodeWithTag("mafeiGlassConfirmScrim")
            .performTouchInput { click(Offset(8f, 8f)) }
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
    }

    @Test
    fun accountRecoveryOverlaysThePageWithoutMovingOrReplacingItsContent() {
        val visible = mutableStateOf(false)
        val dark = mutableStateOf(true)
        val width = mutableStateOf(390)
        composeRule.setContent {
            MaFeiTheme(darkTheme = dark.value) {
                Box(Modifier.size(width.value.dp, 640.dp).testTag("accountScene")) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)
                        .background(MaterialTheme.colorScheme.background)) {
                        Text("最近添加", Modifier.testTag("pageContent"), color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (visible.value) MaFeiGlassConfirmDialog(
                        title = "账号验证", content = "当前账号登录已过期，请重新登录",
                        confirmText = "重新登录", dismissText = "取消", backdrop = backdrop,
                        onConfirm = { confirmed++; visible.value = false },
                        onDismiss = { dismissed++; visible.value = false },
                    )
                }
            }
        }
        for (size in listOf(390, 900)) for (isDark in listOf(true, false)) {
            composeRule.runOnIdle { width.value = size; dark.value = isDark }
            val before = composeRule.onNodeWithTag("pageContent").fetchSemanticsNode().boundsInRoot
            composeRule.runOnIdle { visible.value = true }
            assertEquals(before, composeRule.onNodeWithTag("pageContent").fetchSemanticsNode().boundsInRoot)
            val confirm = composeRule.onNodeWithText("重新登录").assertIsDisplayed()
            confirm.fetchSemanticsNode().boundsInRoot
            for (pressed in listOf(false, true)) {
                if (pressed) confirm.performTouchInput { down(center) }
                System.getenv("MAFEI_CAPTURE_DIR")?.let { directory ->
                    val pixels = composeRule.onNodeWithTag("accountScene").captureToImage().toPixelMap()
                    val bitmap = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
                    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                        bitmap.setRGB(x, y, pixels[x, y].toArgb())
                    }
                    ImageIO.write(bitmap, "png", File(File(directory).apply { mkdirs() },
                        "dialog-$size-${if (isDark) "dark" else "light"}-${if (pressed) "pressed" else "idle"}.png"))
                }
                if (pressed) {
                    // Confirm on release, then reopen to exercise Cancel independently.
                    confirm.performTouchInput { up() }
                    composeRule.runOnIdle { visible.value = true }
                }
            }
            val accepted = confirmed
            composeRule.onNodeWithText("取消").performClick()
            composeRule.onNodeWithText("重新登录").assertDoesNotExist()
            assertEquals(accepted, confirmed)
            assertEquals(before, composeRule.onNodeWithTag("pageContent").fetchSemanticsNode().boundsInRoot)
        }
        assertEquals(4, dismissed)
        assertEquals(4, confirmed)
    }

    @Test
    fun standardActionsUseEqualCapsulesAndRetainPhoneMargins() {
        val width = mutableStateOf(390)
        composeRule.setContent {
            MaFeiTheme {
                Box(Modifier.size(width.value.dp, 640.dp).testTag("scene")) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop).background(Color.DarkGray))
                    MaFeiGlassConfirmDialog("账号验证", "当前账号登录已过期，请重新登录", "重新登录", "取消",
                        {}, {}, backdrop)
                }
            }
        }
        for (size in listOf(320, 390, 900)) {
            composeRule.runOnIdle { width.value = size }
            val scene = composeRule.onNodeWithTag("scene").fetchSemanticsNode().boundsInRoot
            val panel = composeRule.onNodeWithTag("mafeiGlassConfirmCard").fetchSemanticsNode().boundsInRoot
            val cancel = composeRule.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot
            val confirm = composeRule.onNodeWithText("重新登录").fetchSemanticsNode().boundsInRoot
            assertEquals(cancel.width, confirm.width, .5f)
            assertEquals(cancel.top, confirm.top, .5f)
            assertEquals(panel.center.x, (cancel.center.x + confirm.center.x) / 2, .5f)
            assertTrue(panel.left > scene.left && panel.right < scene.right)
            assertTrue(confirm.height >= with(composeRule.density) { 44.dp.toPx() })
        }
    }

    @Test
    fun standardMaterialTracksTheRealBackdropInEveryStyle() {
        val style = mutableStateOf(GlassStyle.Liquid)
        val dark = mutableStateOf(true)
        val source = mutableStateOf(Color.Red)
        composeRule.setContent {
            MaFeiTheme(darkTheme = dark.value) {
                GlassStyleScope(style.value) {
                    Box(Modifier.size(390.dp, 640.dp)) {
                        val backdrop = rememberGlassBackdrop()
                        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop).background(source.value))
                        MaFeiGlassConfirmDialog("账号验证", "当前账号登录已过期，请重新登录", "重新登录", "取消",
                            {}, {}, backdrop)
                    }
                }
            }
        }
        for (material in GlassStyle.entries) for (isDark in listOf(true, false)) {
            composeRule.runOnIdle { style.value = material; dark.value = isDark; source.value = Color.Red }
            val panel = composeRule.onNodeWithTag("mafeiGlassConfirmCard")
            val before = panel.captureToImage().toPixelMap().let { it[it.width / 2, 12] }
            composeRule.runOnIdle { source.value = Color.Cyan }
            val after = panel.captureToImage().toPixelMap().let { it[it.width / 2, 12] }
            assertTrue(abs(before.red - after.red) + abs(before.blue - after.blue) > .15f,
                "$material/$isDark must not replace the page backdrop with an opaque panel: $before -> $after")
            composeRule.onNodeWithText("重新登录").assertIsDisplayed()
        }
    }

    @Test
    fun standaloneFallbackUsesTheSamePanelAndActions() {
        composeRule.setContent {
            MaFeiTheme {
                MaFeiConfirmDialogPanel("删除账号", "确定要删除此账号吗？", "删除", "取消",
                    { confirmed++ }, { dismissed++ }, backdrop = null, destructive = true)
            }
        }
        val cancel = composeRule.onNodeWithText("取消").fetchSemanticsNode().boundsInRoot
        val confirm = composeRule.onNodeWithText("删除").fetchSemanticsNode().boundsInRoot
        assertEquals(cancel.width, confirm.width, .5f)
        composeRule.onNodeWithText("删除").performClick()
        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }

    @Test
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    fun escapeDismissesWithoutConfirming() {
        showDialog()
        composeRule.onNodeWithTag("mafeiGlassConfirmCard").performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, dismissed)
        assertEquals(0, confirmed)
    }
}
