package cn.mafei.cmp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavEntry
import cn.mafei.cmp.common.ui.MaFeiRetainedContent
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.feature.home.MediaBrowserState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class BrowseWarmEntryTest {
    @get:org.junit.Rule
    val rule = createComposeRule()

    @Test
    fun unrelatedShellUpdatesDoNotInvalidateRetainedEntriesButNewInputsReachEveryEntry() {
        var shellRevision by mutableStateOf(0)
        var title by mutableStateOf("First")
        var factoryCalls = 0
        var provider: ((BrowseRoute) -> NavEntry<BrowseRoute>)? = null
        val renders = mutableMapOf<BrowseRoute, Int>()
        val titles = mutableMapOf<BrowseRoute, String>()
        rule.setContent {
            Column {
                BasicText("Shell $shellRevision")
                val currentTitle = title
                val entries = rememberBrowseEntryProvider(currentTitle) {
                    factoryCalls++
                    val content: @Composable (BrowseRoute) -> Unit = { route ->
                        SideEffect {
                            renders[route] = renders.getOrElse(route) { 0 } + 1
                            titles[route] = currentTitle
                        }
                        BasicText(currentTitle)
                    }
                    content
                }
                SideEffect { provider = entries }
                val home = remember(entries) { entries(BrowseHomeRoute) }
                val favorite = remember(entries) { entries(BrowseFavoriteRoute) }
                MaFeiRetainedContent(active = true) { home.Content() }
                MaFeiRetainedContent(active = false) { favorite.Content() }
            }
        }

        var firstProvider: ((BrowseRoute) -> NavEntry<BrowseRoute>)? = null
        var initialRenders: Map<BrowseRoute, Int> = emptyMap()
        rule.runOnIdle {
            firstProvider = provider
            initialRenders = renders.toMap()
            assertEquals(setOf(BrowseHomeRoute, BrowseFavoriteRoute), renders.keys)
            assertEquals(1, factoryCalls)
            shellRevision++
        }
        rule.runOnIdle {
            assertSame(firstProvider, provider)
            assertEquals(1, factoryCalls)
            assertEquals(initialRenders, renders)
            title = "Updated"
        }
        rule.runOnIdle {
            assertSame(firstProvider, provider)
            assertEquals(2, factoryCalls)
            assertEquals(mapOf(BrowseHomeRoute to "Updated", BrowseFavoriteRoute to "Updated"), titles)
            assertEquals(initialRenders.mapValues { it.value + 1 }, renders)
        }
    }

    @Test
    fun hiddenAccountIgnoresForegroundDocumentsAndStillObservesAccountFacts() {
        val browserState = mutableStateOf(MediaBrowserState(activeTabContext = "home"))
        val observed = mutableListOf<BrowseAccountReadState>()
        rule.setContent {
            MaFeiRetainedContent(active = false) {
                AccountReadProbe(browserState, observed)
            }
        }

        var initialCount = 0
        rule.runOnIdle {
            initialCount = observed.size
            browserState.value = browserState.value.copy(
                activeTabContext = "library",
                detailId = MediaId("other-detail"),
                detailLoading = true,
            )
        }
        rule.runOnIdle {
            assertEquals(initialCount, observed.size)
            browserState.value = browserState.value.copy(administrator = true)
        }
        rule.runOnIdle {
            assertEquals(initialCount + 1, observed.size)
            assertEquals(true, observed.last().administrator)
            browserState.value = browserState.value.copy(historyLoading = true, historyError = "Retry history")
        }
        rule.runOnIdle {
            assertEquals(initialCount + 2, observed.size)
            assertEquals(true, observed.last().historyLoading)
            assertEquals("Retry history", observed.last().historyError)
            browserState.value = browserState.value.copy(activeTabContext = "home", detailLoading = false)
        }
        rule.runOnIdle { assertEquals(initialCount + 2, observed.size) }
    }

    @Composable
    private fun AccountReadProbe(source: State<MediaBrowserState>, observed: MutableList<BrowseAccountReadState>) {
        val state by rememberBrowseAccountReadState(source)
        SideEffect { observed += state }
        BasicText(state.historyError.orEmpty())
    }
}
