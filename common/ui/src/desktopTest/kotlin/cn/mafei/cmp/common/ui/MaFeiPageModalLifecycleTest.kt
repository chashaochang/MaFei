package cn.mafei.cmp.common.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaFeiPageModalLifecycleTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    @Test
    fun hidingRetainedPublisherWithdrawsPortalAndReturningRestoresOpenContent() {
        val host = MaFeiWindowModalHostState()
        var active by mutableStateOf(true)
        var open by mutableStateOf(true)
        var publisherCompositions = 0
        var publisherDisposals = 0
        composeRule.setContent {
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(LocalMaFeiWindowModalHost provides host) {
                MaFeiRetainedContent(active) {
                    DisposableEffect(Unit) {
                        publisherCompositions++
                        onDispose { publisherDisposals++ }
                    }
                    MaFeiWindowModalPortal(open) { BasicText("Window modal") }
                }
                host.entry?.let { entry -> Box { entry.content(this, backdrop) } }
            }
        }

        composeRule.onNodeWithText("Window modal").assertIsDisplayed()
        composeRule.runOnIdle {
            assertNotNull(host.entry)
            active = false
        }
        composeRule.onNodeWithText("Window modal").assertDoesNotExist()
        composeRule.runOnIdle {
            assertNull(host.entry)
            assertTrue(open)
            assertEquals(1, publisherCompositions)
            assertEquals(0, publisherDisposals)
            active = true
        }
        composeRule.onNodeWithText("Window modal").assertIsDisplayed()
        composeRule.runOnIdle { open = false }
        composeRule.onNodeWithText("Window modal").assertDoesNotExist()
    }

    @Test
    fun hidingPreviousPublisherDoesNotDismissAnotherPagesPortal() {
        val host = MaFeiWindowModalHostState()
        var firstActive by mutableStateOf(true)
        var secondOpen by mutableStateOf(false)
        composeRule.setContent {
            val backdrop = rememberGlassBackdrop()
            CompositionLocalProvider(LocalMaFeiWindowModalHost provides host) {
                CompositionLocalProvider(LocalMaFeiPageActive provides firstActive) {
                    MaFeiWindowModalPortal(true) { BasicText("First modal") }
                }
                MaFeiWindowModalPortal(secondOpen) { BasicText("Second modal") }
                host.entry?.let { entry -> Box { entry.content(this, backdrop) } }
            }
        }

        composeRule.onNodeWithText("First modal").assertIsDisplayed()
        composeRule.runOnIdle { secondOpen = true }
        composeRule.onNodeWithText("Second modal").assertIsDisplayed()
        composeRule.runOnIdle { firstActive = false }
        composeRule.onNodeWithText("Second modal").assertIsDisplayed()
        composeRule.onNodeWithText("First modal").assertDoesNotExist()
    }

    @Test
    fun inactiveLegacySheetDoesNotCreateAWindowAndRetainsItsOpenState() {
        checkLegacyWindowLifecycle(sheet = true)
    }

    @Test
    fun inactiveLegacyConfirmationDoesNotCreateAWindowAndRetainsItsOpenState() {
        checkLegacyWindowLifecycle(sheet = false)
    }

    private fun checkLegacyWindowLifecycle(sheet: Boolean) {
        var active by mutableStateOf(false)
        var open by mutableStateOf(true)
        var dismissed = 0
        composeRule.setContent {
            MaterialTheme {
                MaFeiRetainedContent(active) {
                    if (open) {
                        val onDismiss = { dismissed++; open = false }
                        if (sheet) {
                            LegacyModalSheet("Retained sheet", onDismiss) {
                                BasicText("Modal content")
                            }
                        } else {
                            LegacyConfirmDialog(
                                "Retained confirmation", "Modal content", "Confirm", "Cancel",
                                onConfirm = {}, onDismiss = onDismiss,
                            )
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Modal content").assertDoesNotExist()
        composeRule.runOnIdle { active = true }
        composeRule.onNodeWithText("Modal content").assertIsDisplayed()
        composeRule.runOnIdle { active = false }
        composeRule.onNodeWithText("Modal content").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(open)
            assertEquals(0, dismissed)
            active = true
        }
        composeRule.onNodeWithText("Modal content").assertIsDisplayed()
    }

    @Test
    fun lateDialogOnInactiveVisiblePageDoesNotStealForegroundFocus() {
        var active by mutableStateOf(false)
        var open by mutableStateOf(false)
        var dismissed = 0
        val foregroundFocus = FocusRequester()
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop))
                    BasicText(
                        "Foreground page",
                        Modifier.testTag("foreground").focusRequester(foregroundFocus).focusable(),
                    )
                    // Desktop glass sampling keeps the background page placed but inactive.
                    CompositionLocalProvider(LocalMaFeiPageActive provides active) {
                        if (open) MaFeiGlassConfirmDialog(
                            "Background error", "Could not load", "OK", null,
                            onConfirm = { open = false },
                            onDismiss = { dismissed++; open = false },
                            backdrop = backdrop,
                            modifier = Modifier.testTag("dialog"),
                        )
                    }
                }
            }
        }

        composeRule.runOnIdle { foregroundFocus.requestFocus() }
        composeRule.onNodeWithTag("foreground").assertIsFocused()
        composeRule.runOnIdle { open = true }
        composeRule.onNodeWithTag("dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("foreground").assertIsFocused()
        composeRule.runOnIdle { active = true }
        composeRule.onNodeWithTag("dialog").assertIsDisplayed().assertIsFocused()
        composeRule.runOnIdle { active = false }
        composeRule.onNodeWithTag("dialog").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue(open)
            assertEquals(0, dismissed)
            active = true
        }
        composeRule.onNodeWithTag("dialog").assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun returningToPageDoesNotRedisplayAnAlreadyDismissedError() {
        var active by mutableStateOf(true)
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.fillMaxSize().captureGlassBackdrop(backdrop))
                    CompositionLocalProvider(LocalMaFeiPageActive provides active) {
                        MaFeiErrorDialog("Load failed", backdrop)
                    }
                }
            }
        }

        composeRule.onNodeWithText("Load failed").assertIsDisplayed()
        composeRule.onNodeWithText("知道了").performClick()
        composeRule.onNodeWithText("Load failed").assertDoesNotExist()
        composeRule.runOnIdle { active = false }
        composeRule.runOnIdle { active = true }
        composeRule.onNodeWithText("Load failed").assertDoesNotExist()
    }
}
