package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.core.domain.ProviderKind
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProviderConnectionTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test
    fun providerSelectionKeepsTheAddressAndClearsPasswordsBeforePlexAuthorization() {
        val form = ConnectFormState().apply {
            baseUrl = "https://media.example"
            username = "demo"
            password = "fixture-password"
        }
        var changes = 0
        var authorizations = 0
        var submission: ProviderKind? = null
        scene {
            ConnectScreen(
                busy = false,
                formState = form,
                onSubmit = { provider, _, _, _, _ -> submission = provider },
                onProviderChanged = { changes++ },
                plexActions = PlexConnectActions(authorize = { authorizations++ }),
            )
        }
        for ((name, provider) in listOf(
            "Emby" to ProviderKind.Emby,
            "飞牛影视" to ProviderKind.Feiniu,
            "Jellyfin" to ProviderKind.Jellyfin,
        )) {
            click(compose.onNodeWithContentDescription(name))
            compose.runOnIdle {
                assertEquals(provider, form.provider)
                assertEquals("https://media.example", form.baseUrl)
                assertEquals("demo", form.username)
                assertEquals("", form.password)
            }
            click(compose.onNodeWithText("登录"))
            compose.runOnIdle { assertEquals(provider, submission) }
            compose.runOnIdle { form.password = "fixture-password" }
        }
        click(compose.onNodeWithContentDescription("Plex"))
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        click(compose.onNodeWithText("连接 Plex"))
        compose.runOnIdle {
            assertEquals(ProviderKind.Plex, form.provider)
            assertEquals("", form.password)
            assertEquals(4, changes)
            assertEquals(1, authorizations)
        }
    }

    @Test
    fun plexPhasesFitPhoneAndWideWindowsAndKeepActionsStableDuringPress() {
        val width = mutableStateOf(390)
        val dark = mutableStateOf(true)
        val busy = mutableStateOf(false)
        val state = mutableStateOf(PlexConnectUiState())
        val form = ConnectFormState().apply { provider = ProviderKind.Plex }
        val longName = "Living room media server with a very long name for family and shared movies"
        val servers = listOf(PlexConnectServer("one", longName), PlexConnectServer("two", "Cinema"))
        var selected: String? = null
        var cancelled = 0
        var reopened = 0
        scene(width = { width.value }, dark = { dark.value }) {
            ConnectScreen(
                busy = busy.value,
                formState = form,
                onSubmit = { _, _, _, _, _ -> error("Plex must use browser authorization") },
                plexState = state.value,
                plexActions = PlexConnectActions(
                    openBrowser = { reopened++ },
                    connect = { selected = it },
                    cancel = { cancelled++ },
                ),
            )
        }
        for (size in listOf(390, 900)) for (isDark in listOf(true, false)) {
            compose.runOnIdle { width.value = size; dark.value = isDark }
            for (phase in PlexConnectPhase.entries) {
                compose.runOnIdle {
                    state.value = PlexConnectUiState(phase, servers)
                    busy.value = phase == PlexConnectPhase.Connecting
                }
                val action = compose.onNodeWithText(when (phase) {
                    PlexConnectPhase.Idle -> "连接 Plex"
                    PlexConnectPhase.Waiting -> "打开 Plex"
                    else -> "取消"
                })
                assertInsideScene(action)
                val actionBounds = action.fetchSemanticsNode().boundsInRoot
                if (phase == PlexConnectPhase.Choosing || phase == PlexConnectPhase.Connecting) {
                    val server = compose.onNodeWithText(longName)
                    assertInsideScene(server)
                    val layouts = mutableListOf<TextLayoutResult>()
                    server.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertEquals(1, layouts.single().lineCount)
                    assertTrue(layouts.single().size.height <= server.fetchSemanticsNode().boundsInRoot.height)
                    if (phase == PlexConnectPhase.Connecting) server.assertIsNotEnabled()
                    else {
                        click(server)
                        compose.runOnIdle { assertEquals("one", selected) }
                    }
                }
                val suffix = "$size-${if (isDark) "dark" else "light"}-${phase.name.lowercase()}"
                capture("plex-$suffix-idle")
                action.performTouchInput { down(center) }
                compose.mainClock.advanceTimeBy(100)
                val pressed = action.fetchSemanticsNode().boundsInRoot
                assertTrue(abs(actionBounds.center.x - pressed.center.x) < 1f)
                assertTrue(abs(actionBounds.center.y - pressed.center.y) < 1f)
                assertInsideScene(action)
                capture("plex-$suffix-pressed")
                action.performTouchInput { up() }
            }
        }
        compose.runOnIdle {
            assertEquals(12, cancelled)
            assertEquals(4, reopened)
        }
    }

    @Test
    fun longServerListsRemainScrollableToTheLastChoiceAndCancellation() {
        val servers = (1..18).map { PlexConnectServer("$it", "Server $it") }
        var selected: String? = null
        var cancelled = false
        scene {
            ConnectScreen(
                busy = false,
                formState = ConnectFormState().apply { provider = ProviderKind.Plex },
                onSubmit = { _, _, _, _, _ -> },
                plexState = PlexConnectUiState(PlexConnectPhase.Choosing, servers),
                plexActions = PlexConnectActions(connect = { selected = it }, cancel = { cancelled = true }),
            )
        }
        val last = compose.onNodeWithText("Server 18").performScrollTo()
        click(last)
        compose.runOnIdle { assertEquals("18", selected) }
        val cancel = compose.onNodeWithText("取消").performScrollTo()
        click(cancel)
        compose.runOnIdle { assertTrue(cancelled) }
    }

    private fun scene(
        width: () -> Int = { 390 },
        dark: () -> Boolean = { true },
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        compose.setContent {
            val window = object : WindowInfo {
                override val isWindowFocused = true
                override val containerSize = IntSize(width(), 700)
            }
            CompositionLocalProvider(LocalWindowInfo provides window, LocalDensity provides Density(1f)) {
                MaFeiTheme(darkTheme = dark()) {
                    Box(Modifier.requiredSize(width().dp, 700.dp).testTag("providerScene")) { content() }
                }
            }
        }
    }

    private fun click(node: SemanticsNodeInteraction) {
        node.fetchSemanticsNode().boundsInRoot.let { assertTrue(it.width > 0 && it.height > 0) }
        node.performTouchInput { click(center) }
    }

    private fun assertInsideScene(node: SemanticsNodeInteraction) {
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val scene = compose.onNodeWithTag("providerScene").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 0 && bounds.height > 0)
        assertTrue(scene.contains(bounds.topLeft) && scene.contains(bounds.bottomRight), "$bounds outside $scene")
    }

    private fun capture(name: String) {
        val directory = System.getenv("MAFEI_CAPTURE_DIR") ?: return
        val pixels = compose.onNodeWithTag("providerScene").captureToImage().toPixelMap()
        val bitmap = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            bitmap.setRGB(x, y, pixels[x, y].toArgb())
        }
        ImageIO.write(bitmap, "png", File(File(directory).apply { mkdirs() }, "$name.png"))
    }
}
