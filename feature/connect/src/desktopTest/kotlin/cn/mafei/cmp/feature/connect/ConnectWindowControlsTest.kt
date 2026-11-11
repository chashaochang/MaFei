package cn.mafei.cmp.feature.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.MaFeiInsetsDp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.design_system.MaFeiWindowFeatures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ConnectWindowControlsTest {
    @get:org.junit.Rule val compose = createComposeRule()

    @Test
    fun providerSwitcherTracksWindowControlsAndRestoresItsNormalPosition() {
        val features = mutableStateOf(
            MaFeiWindowFeatures(safeInsets = MaFeiInsetsDp(top = 24f)),
        )
        compose.setContent {
            val window = object : WindowInfo {
                override val isWindowFocused = true
                override val containerSize = IntSize(900, 700)
            }
            CompositionLocalProvider(
                LocalWindowInfo provides window,
                LocalDensity provides Density(1f),
                LocalMaFeiWindowFeatures provides features.value,
            ) {
                MaFeiTheme(darkTheme = true) {
                    Box(Modifier.requiredSize(900.dp, 700.dp)) {
                        ConnectScreen(busy = false, onSubmit = { _, _, _, _, _ -> })
                    }
                }
            }
        }

        fun switcherTop() = compose.onNodeWithTag(
            "liquid-bottom-tabs",
            useUnmergedTree = true,
        ).fetchSemanticsNode().boundsInRoot.top

        val normalTop = switcherTop()

        compose.runOnIdle {
            // A narrow PC window need not have desktopWindow set.
            features.value = features.value.copy(windowedHost = true, windowControlTopInsetDp = 64f)
        }
        assertEquals(normalTop + 40f, switcherTop(), 0.5f)
        assertTrue(switcherTop() >= 64f)

        compose.runOnIdle { features.value = features.value.copy(windowControlTopInsetDp = 84f) }
        assertEquals(normalTop + 60f, switcherTop(), 0.5f)

        compose.runOnIdle { features.value = features.value.copy(windowFullscreen = true) }
        assertEquals(normalTop, switcherTop(), 0.5f)

        compose.runOnIdle {
            features.value = features.value.copy(windowedHost = false, windowFullscreen = false)
        }
        assertEquals(normalTop, switcherTop(), 0.5f)
    }
}
