package cn.mafei.cmp.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.glass.components.LiquidToggleDefaults
import cn.mafei.cmp.common.glass.components.LiquidToggle
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeCarouselSettingTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test fun phoneSwitchKeepsRowToggleWorkingWithTheLongerTrack() = verifySwitch(390)

    @Test fun wideSwitchKeepsTheSameCompactProportions() = verifySwitch(900)

    @Test fun holdingSwitchDoesNotPressItsParentAndReleaseChangesItOnlyOnce() = verifyIndependentPress(false)

    @Test fun holdingSwitchInDarkThemeDoesNotPressItsParent() = verifyIndependentPress(true)

    private fun verifyIndependentPress(dark: Boolean) {
        val enabled = mutableStateOf(false)
        var changes = 0
        showSettings(enabled, { enabled.value = it; changes++ }, dark = dark)
        val row = compose.onNodeWithContentDescription("首页轮播图", useUnmergedTree = true)
        val toggle = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch), useUnmergedTree = true,
        )
        val before = row.captureToImage().toPixelMap()
        val background = before[6, before.height / 2]
        compose.mainClock.autoAdvance = false
        toggle.performTouchInput { down(Offset(right - 4f, centerY)) }
        compose.mainClock.advanceTimeBy(240)
        val pressed = row.captureToImage().toPixelMap()
        assertEquals(background, pressed[6, pressed.height / 2], "Switch press must not tint or shrink the settings row")
        assertEquals(0, changes)
        toggle.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, changes)
        assertEquals(true, enabled.value)
    }

    @Test fun repeatedTrackClicksUseTheCurrentStateAndNeverDoubleCommit() {
        val enabled = mutableStateOf(false)
        var changes = 0
        showSettings(enabled, { enabled.value = it; changes++ })
        val toggle = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch), useUnmergedTree = true,
        )
        repeat(4) { index ->
            toggle.performTouchInput { click(Offset(right - 4f, centerY)) }
            compose.waitForIdle()
            assertEquals(index % 2 == 0, enabled.value)
            assertEquals(index + 1, changes)
        }
    }

    @Test fun cancellingATrackPressDoesNotChangeTheSetting() {
        val enabled = mutableStateOf(false)
        var changes = 0
        showSettings(enabled, { enabled.value = it; changes++ })
        val toggle = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch), useUnmergedTree = true,
        )
        toggle.performTouchInput { down(center); cancel() }
        compose.waitForIdle()
        assertEquals(false, enabled.value)
        assertEquals(0, changes)
    }

    @Test fun heldGlassSamplesItsLiveSourceRatherThanShowingTheUnsampledBackground() {
        val source = mutableStateOf(Color.Red)
        compose.setContent {
            MaterialTheme {
                val backdrop = rememberGlassBackdrop()
                Box(Modifier.size(120.dp, 80.dp)) {
                    Box(Modifier.size(120.dp, 80.dp).captureGlassBackdrop(backdrop).background(source.value))
                    // Cover the source so a transparent thumb cannot pass this test.
                    Box(Modifier.size(120.dp, 80.dp).background(Color.Black))
                    LiquidToggle({ false }, {}, backdrop, Modifier.offset(32.dp, 18.dp))
                }
            }
        }
        val toggle = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch), useUnmergedTree = true,
        )
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        toggle.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        fun sample(): Color {
            val pixels = toggle.captureToImage().toPixelMap()
            return pixels[pixels.width * 16 / 58, pixels.height / 2]
        }
        val red = sample()
        assertTrue(red.red > red.blue + .3f, "Held glass must sample red over the visible black backdrop: $red")
        source.value = Color.Blue
        compose.mainClock.advanceTimeBy(32)
        val blue = sample()
        assertTrue(blue.blue > blue.red + .3f, "The source must update while held: $blue")
        toggle.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
    }

    private fun verifySwitch(width: Int) {
        val enabled = mutableStateOf(false)
        var changes = 0
        showSettings(enabled, { enabled.value = it; changes++ }, width = width)
        val row = compose.onNodeWithContentDescription("首页轮播图", useUnmergedTree = true)
        val thumb = compose.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch), useUnmergedTree = true,
        )
        thumb.assertWidthIsEqualTo(58.dp).assertHeightIsEqualTo(44.dp)
        assertEquals(58.dp, LiquidToggleDefaults.trackWidth)
        assertEquals(32.dp, LiquidToggleDefaults.trackHeight)
        assertEquals(44.dp, LiquidToggleDefaults.touchHeight)
        val rowBounds = row.fetchSemanticsNode().boundsInRoot
        val thumbBounds = thumb.fetchSemanticsNode().boundsInRoot
        val density = thumbBounds.width / 58
        assertEquals(rowBounds.right - (16 + 58) * density, thumbBounds.left, absoluteTolerance = 1f)
        row.performClick()
        compose.waitForIdle()
        row.assertIsSelected()
        assertEquals(1, changes)
        thumb.assertWidthIsEqualTo(58.dp).assertHeightIsEqualTo(44.dp)
        assertEquals(rowBounds.right - 16 * density, thumb.fetchSemanticsNode().boundsInRoot.right, absoluteTolerance = 1f)
        row.performClick()
        compose.waitForIdle()
        row.assertIsNotSelected()
        assertEquals(2, changes)
    }

    private fun showSettings(
        enabled: State<Boolean>, onChange: (Boolean) -> Unit,
        width: Int = 390, dark: Boolean = false,
    ) {
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Box(Modifier.size(width.dp, 700.dp)) {
                    SettingsScreen(
                        onBack = {}, onOpenAppearance = {}, homeCarouselVisible = { enabled.value },
                        onHomeCarouselVisibleChanged = onChange,
                    )
                }
            }
        }
    }
}
