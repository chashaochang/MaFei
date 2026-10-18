package cn.mafei.cmp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.common.ui.MaFeiRetainedContent
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.feature.connect.ConnectErrorDialog
import cn.mafei.cmp.feature.photo.PhotoViewerScreen
import kotlin.test.*

class RetainedPageTimerTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun hiddenSlideshowKeepsItsPhotoAndEnabledStateUntilReactivation() {
        val active = mutableStateOf(true)
        val photos = (0..2).map { MediaItem(MediaId("photo-$it"), ServerId("server"), ProviderKind.Jellyfin, "Photo $it", MediaType.Photo) }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.size(390.dp, 844.dp)) {
                    MaFeiRetainedContent(active.value) {
                        PhotoViewerScreen(photos, "photo-0", onBack = {}, onFavorite = {}, favoriteBusy = false,
                            onSave = null, saving = false, info = null, infoLoading = false, infoFailed = false,
                            onInfo = {}, artwork = { item, modifier -> Box(modifier.testTag(item.id.value).background(Color.Gray)) })
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(48)
        val play = rule.onNodeWithContentDescription("播放幻灯片")
        play.fetchSemanticsNode().boundsInRoot
        play.performClick()
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle { active.value = false }
        rule.mainClock.advanceTimeBy(48)
        rule.mainClock.advanceTimeBy(20_000)
        rule.runOnIdle { active.value = true }
        rule.mainClock.advanceTimeBy(48)
        rule.onNodeWithTag("photo-0").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(6_000)
        rule.onNodeWithTag("photo-1").assertIsDisplayed()
    }

    @Test fun hiddenConnectionFeedbackCannotHideTheForegroundKeyboard() {
        val active = mutableStateOf(false)
        val error = mutableStateOf<String?>("First failure")
        var hides = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() = Unit
            override fun hide() { hides++ }
        }
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.size(390.dp, 844.dp)) {
                        MaFeiRetainedContent(active.value) { ConnectErrorDialog(error.value, {}, backdrop) }
                    }
                }
            }
        }
        rule.waitForIdle()
        assertEquals(0, hides)
        rule.runOnIdle { error.value = "Another failure" }
        rule.waitForIdle()
        assertEquals(0, hides)
        rule.runOnIdle { active.value = true }
        rule.waitForIdle()
        assertEquals(1, hides)
        rule.runOnIdle { active.value = false; error.value = "Background failure" }
        rule.waitForIdle()
        assertEquals(1, hides)
        rule.runOnIdle { active.value = true }
        rule.waitForIdle()
        assertEquals(2, hides)
    }
}
