package cn.mafei.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule

class RetainedAccountContentTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun unrelatedShellUpdatesDoNotReexecuteHiddenAccountButChangedInputsReachIt() {
        var shellRevision by mutableStateOf(0)
        var presented by mutableStateOf(true)
        var title by mutableStateOf("First")
        var navRenders = 0
        var mineRenders = 0
        var creations = 0
        var factoryCalls = 0
        val accountKey = Any()
        var token: Any? = null
        var observedTitle: String? = null
        val backStack: List<BrowseRoute> = listOf(BrowseMineRoute)
        val onBack: () -> Unit = {}
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DesktopNavigationOwner {
                    Column {
                        BasicText("Shell $shellRevision")
                        val currentTitle = title
                        // Exercise the production content factory around the real host
                        // and NavDisplay, including both nested account renderers.
                        val retainedContent = rememberRetainedAccountContent(accountKey, currentTitle) {
                            factoryCalls++
                            val mineContent: @Composable (Modifier) -> Unit = { modifier ->
                                val identity = remember { Any() }
                                DisposableEffect(identity) {
                                    creations++
                                    token = identity
                                    onDispose { }
                                }
                                SideEffect {
                                    mineRenders++
                                    observedTitle = currentTitle
                                }
                                BasicText(currentTitle, modifier)
                            }
                            val accountNavContent: @Composable (Modifier) -> Unit = { modifier ->
                                SideEffect { navRenders++ }
                                NavDisplay(
                                    backStack = backStack,
                                    onBack = { onBack() },
                                    entryProvider = { route -> NavEntry(route) { mineContent(Modifier) } },
                                    modifier = modifier,
                                )
                            }
                            accountNavContent
                        }
                        Box(Modifier.size(400.dp, 600.dp)) {
                            RetainedPhoneAccountHost(phone = true, presented = presented, content = retainedContent)
                        }
                    }
                }
            }
        }

        rule.runOnIdle { presented = false }
        var initialNavRenders = 0
        var initialMineRenders = 0
        var initialToken: Any? = null
        rule.runOnIdle {
            initialNavRenders = navRenders
            initialMineRenders = mineRenders
            initialToken = token
            assertEquals(1, creations)
            assertEquals(1, factoryCalls)
            shellRevision++
        }
        var shellDeltas: Pair<Int, Int>? = null
        rule.runOnIdle {
            shellDeltas = (navRenders - initialNavRenders) to (mineRenders - initialMineRenders)
            assertSame(initialToken, token)
            assertEquals(1, creations)
            assertEquals(1, factoryCalls)
            title = "Updated"
        }
        rule.runOnIdle {
            assertEquals(2, factoryCalls)
            assertEquals("Updated", observedTitle)
            assertTrue(mineRenders > initialMineRenders)
            assertSame(initialToken, token)
            assertEquals(1, creations)
        }
        assertEquals(0 to 0, shellDeltas, "Unrelated shell update reexecuted (account nav, Mine) renderers")
    }
}
