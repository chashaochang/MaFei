package cn.mafei.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.junit.Rule

class ApplicationNavDisplayRecompositionTest {
    @get:Rule val rule = createComposeRule()
    private val navigation = BrowseNavigationState()
    private val roots: List<BrowseRoute> = listOf(BrowseHomeRoute, BrowseLibraryIndexRoute, BrowseFavoriteRoute)
    private val renders = mutableMapOf<BrowseRoute, Int>()
    private val creations = mutableMapOf<BrowseRoute, Int>()
    private val tokens = mutableMapOf<BrowseRoute, Any>()
    private var factoryCalls = 0
    private val onBack: () -> Unit = {
        if (navigation.browseBackStack.size > 1) navigation.browseBackStack.removeLastOrNull()
    }

    @Test
    fun warmRootSwitchDoesNotReexecuteFixedInputPageBodies() {
        showNavigation()
        roots.drop(1).forEach(::selectRoot)
        selectRoot(BrowseHomeRoute)

        var initialTokens: Map<BrowseRoute, Any> = emptyMap()
        rule.runOnIdle {
            assertEquals(roots.toSet(), renders.keys)
            assertEquals(1, factoryCalls)
            initialTokens = tokens.toMap()
        }

        val observations = mutableListOf<String>()
        val unexpectedRenders = mutableListOf<String>()
        repeat(2) {
            listOf(BrowseLibraryIndexRoute, BrowseHomeRoute).forEach { incoming ->
                var outgoing: BrowseRoute = BrowseHomeRoute
                var before: Map<BrowseRoute, Int> = emptyMap()
                rule.runOnIdle {
                    outgoing = navigation.activeRoot
                    before = renders.toMap()
                }
                selectRoot(incoming)
                rule.onNodeWithTag(pageTag(incoming)).assertIsDisplayed()
                rule.runOnIdle {
                    val deltas = roots.associateWith { renders.getValue(it) - before.getValue(it) }
                    val observation = "$outgoing -> $incoming: " +
                        "outgoing=${deltas.getValue(outgoing)}, " +
                        "incoming=${deltas.getValue(incoming)}, " +
                        "third-hidden=${deltas.getValue(BrowseFavoriteRoute)}"
                    observations += observation
                    if (deltas.values.any { it != 0 }) unexpectedRenders += observation
                    roots.forEach { root ->
                        assertSame(initialTokens.getValue(root), tokens.getValue(root), "Recreated $root")
                        assertEquals(1, creations.getValue(root), "Recreated $root")
                    }
                    assertEquals(1, factoryCalls)
                }
            }
        }

        assertEquals(
            emptyList(),
            unexpectedRenders,
            "Fixed-input page bodies must not observe root activity. All switches:\n${observations.joinToString("\n")}",
        )
    }

    private fun showNavigation() {
        rule.mainClock.autoAdvance = false
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DesktopNavigationOwner {
                    val entries = rememberBrowseEntryProvider {
                        factoryCalls++
                        // No activity, lifecycle, navigation or transition local is read here.
                        // Count this renderer, rather than a separate skippable leaf composable.
                        val content: @Composable (BrowseRoute) -> Unit = { route ->
                            val token = remember { Any() }
                            DisposableEffect(route, token) {
                                tokens[route] = token
                                creations[route] = creations.getOrElse(route) { 0 } + 1
                                onDispose { }
                            }
                            SideEffect { renders[route] = renders.getOrElse(route) { 0 } + 1 }
                            BasicText(route.toString(), Modifier.testTag(pageTag(route)))
                        }
                        content
                    }
                    Box(Modifier.size(400.dp, 600.dp)) {
                        ApplicationNavDisplay(
                            navigation = navigation,
                            onBack = onBack,
                            entryProvider = entries,
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun selectRoot(root: BrowseRoute) {
        rule.runOnIdle { navigation.selectRoot(root) }
        rule.mainClock.advanceTimeBy(500)
    }

    private fun pageTag(route: BrowseRoute) = "fixed-page-$route"
}
