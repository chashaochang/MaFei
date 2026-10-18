package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.core.domain.ProviderKind
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ConnectFeedbackTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test
    fun failureOverlaysTheUnchangedDraftAndCanBeDismissedBeforeRepeatingLogin() {
        val error = mutableStateOf<String?>(null)
        val busy = mutableStateOf(false)
        val dark = mutableStateOf(true)
        val width = mutableStateOf(390)
        val form = ConnectFormState().apply {
            baseUrl = "https://media.example"
            username = "demo"
            password = "test-password"
            rememberPassword = false
        }
        var submissions = 0
        val failure = "无法连接服务器，请检查地址后重试"
        compose.setContent {
            val window = object : WindowInfo {
                override val isWindowFocused = true
                override val containerSize = IntSize(width.value, 700)
            }
            CompositionLocalProvider(LocalWindowInfo provides window) {
                MaFeiTheme(darkTheme = dark.value) {
                    Box(Modifier.requiredSize(width.value.dp, 700.dp).testTag("loginScene")) {
                        val backdrop = rememberGlassBackdrop()
                        Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop)) {
                            ConnectScreen(
                                busy = busy.value,
                                formState = form,
                                onSubmit = { provider, address, name, password, remember ->
                                    assertEquals(ProviderKind.Jellyfin, provider)
                                    assertEquals("https://media.example", address)
                                    assertEquals("demo", name)
                                    assertEquals("test-password", password)
                                    assertEquals(false, remember)
                                    submissions++
                                    error.value = null
                                    busy.value = true
                                },
                            )
                        }
                        ConnectErrorDialog(error.value, { error.value = null }, backdrop)
                    }
                }
            }
        }
        for (size in listOf(390, 900)) for (isDark in listOf(true, false)) {
            compose.runOnIdle { width.value = size; dark.value = isDark }
            val loginBounds = compose.onNodeWithText("登录").fetchSemanticsNode().boundsInRoot
            val fields = compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            compose.onNodeWithText("登录").performClick()
            compose.runOnIdle { busy.value = false; error.value = failure }

            compose.onNodeWithText(failure).assertIsDisplayed()
                .assert(hasAnyAncestor(hasTestTag("mafeiGlassConfirmCard")))
            assertEquals(loginBounds, compose.onNodeWithText("登录").fetchSemanticsNode().boundsInRoot)
            assertEquals(fields, compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().map { it.boundsInRoot })

            val acknowledge = compose.onNodeWithText("知道了")
            acknowledge.fetchSemanticsNode().boundsInRoot
            capture("login-$size-${if (isDark) "dark" else "light"}-idle")
            acknowledge.performTouchInput { down(center) }
            compose.mainClock.advanceTimeBy(100)
            capture("login-$size-${if (isDark) "dark" else "light"}-pressed")
            acknowledge.performTouchInput { up() }
            compose.onNodeWithText(failure).assertDoesNotExist()
            compose.runOnIdle { assertEquals(null, error.value) }

            // A repeated failure with identical text must be presented again.
            compose.onNodeWithText("登录").performClick()
            compose.runOnIdle { busy.value = false; error.value = failure }
            compose.onNodeWithText(failure).assertIsDisplayed()
            compose.onNodeWithTag("mafeiGlassConfirmCard").performKeyInput { pressKey(Key.Escape) }
            compose.onNodeWithText(failure).assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(null, error.value)
                assertEquals("https://media.example", form.baseUrl)
                assertEquals("demo", form.username)
                assertEquals("test-password", form.password)
                assertEquals(false, form.rememberPassword)
            }
        }
        assertEquals(8, submissions)
    }

    private fun capture(name: String) {
        val directory = System.getenv("MAFEI_CAPTURE_DIR") ?: return
        val pixels = compose.onNodeWithTag("loginScene").captureToImage().toPixelMap()
        val bitmap = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            bitmap.setRGB(x, y, pixels[x, y].toArgb())
        }
        ImageIO.write(bitmap, "png", File(File(directory).apply { mkdirs() }, "$name.png"))
    }
}
