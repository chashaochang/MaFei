package cn.mafei.cmp.feature.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.glass.captureGlassBackdrop
import cn.mafei.cmp.common.glass.rememberGlassBackdrop
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaPerson
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonDetailContrastTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun phoneTextDoesNotInheritBlackFromTheHost() = checkPageText(360.dp)

    @Test fun wideTextDoesNotInheritBlackFromTheHost() = checkPageText(900.dp)

    private fun checkPageText(width: Dp) {
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    Box(Modifier.requiredSize(width, 800.dp)) {
                        PersonDetailScreen(
                            person = MediaPerson(MediaId("actor"), ServerId("server"), ProviderKind.Jellyfin,
                                "演员", overview = "演员简介"),
                            movies = listOf(MediaItem(MediaId("movie"), ServerId("server"),
                                ProviderKind.Jellyfin, "演员作品")),
                            series = emptyList(), episodes = emptyList(),
                            loading = false, error = null, onBack = {}, onRetry = {}, onItem = {},
                        )
                    }
                }
            }
        }
        assertTextColor("演员简介", Color.White.copy(alpha = .86f))
        assertTextColor("电影", Color.White)
        assertTextColor("演员作品", Color.White)
    }

    @Test fun expandedBiographyRemainsReadableWithABlackHostContentColor() {
        rule.setContent {
            MaFeiTheme {
                CompositionLocalProvider(LocalContentColor provides Color.Black) {
                    val backdrop = rememberGlassBackdrop()
                    Box(Modifier.requiredSize(360.dp, 800.dp)) {
                        Box(Modifier.requiredSize(360.dp, 800.dp).captureGlassBackdrop(backdrop))
                        OverviewSheetOverlay("完整演员简介", backdrop, onDismiss = {})
                    }
                }
            }
        }
        assertTextColor("完整演员简介", Color.White.copy(alpha = .92f))
    }

    private fun assertTextColor(text: String, expected: Color) {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            assertTrue(it(layouts))
        }
        assertEquals(expected, layouts.single().layoutInput.style.color, text)
    }
}
