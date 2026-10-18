package cn.mafei.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigationevent.DirectNavigationEventInputHandler
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule

class RetainedPhoneAccountHostTest {
    @get:Rule val rule = createComposeRule()
    private var phone by mutableStateOf(true)
    private var presented by mutableStateOf(false)
    private var session by mutableStateOf(0)
    private var created = 0
    private var disposed = 0
    private var draws = 0
    private var identity: Any? = null
    private lateinit var pageLifecycle: Lifecycle
    private lateinit var rootDispatcher: NavigationEventDispatcher
    private lateinit var pageDispatcher: NavigationEventDispatcher
    private lateinit var pageScroll: LazyListState
    private lateinit var pageScope: CoroutineScope
    private var selectPage: (String) -> Unit = {}
    private val backEvents = mutableListOf<String>()

    @Test
    fun accountContentIsLazyUntilFirstVisitAndAWideVisitCanReturnToPhone() {
        phone = false
        showHosts()
        rule.onNodeWithTag("account-content", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { assertEquals(0, created); presented = true }
        rule.onNodeWithTag("wide-account").assertIsDisplayed()
        lateinit var original: Any
        rule.runOnIdle {
            assertEquals(1, created)
            original = checkNotNull(identity)
            selectPage("settings")
            presented = false
        }
        rule.onNodeWithTag("wide-account").assertDoesNotExist()
        rule.onNodeWithTag("account-content", useUnmergedTree = true).assertExists().assertIsNotDisplayed()
        rule.runOnIdle { assertSame(original, identity); phone = true }
        rule.runOnIdle { presented = true }
        rule.onNodeWithText("settings").assertIsDisplayed()
        rule.runOnIdle {
            assertSame(original, identity)
            assertEquals(1, created)
            assertEquals(0, disposed)
        }
    }

    @Test
    fun hidingPhoneTabKeepsRememberedStateWithoutContentSemanticsOrDrawing() {
        presented = true
        showHosts()
        lateinit var original: Any
        rule.runOnIdle {
            original = checkNotNull(identity)
            selectPage("settings")
        }
        rule.onNodeWithText("settings").assertIsDisplayed()
        rule.onNodeWithTag("stage").captureToImage()
        rule.runOnIdle { presented = false }
        rule.onNodeWithTag("account-content").assertDoesNotExist()
        rule.onNodeWithTag("account-content", useUnmergedTree = true).assertExists().assertIsNotDisplayed()
        var hiddenDraws = 0
        rule.runOnIdle { hiddenDraws = draws }
        rule.onNodeWithTag("stage").captureToImage()
        rule.runOnIdle {
            assertEquals(hiddenDraws, draws)
            assertEquals(1, created)
            assertEquals(0, disposed)
            assertSame(original, identity)
            presented = true
        }
        rule.onNodeWithText("settings").assertIsDisplayed()
        rule.runOnIdle {
            assertSame(original, identity)
            assertEquals(1, created)
        }
    }

    @Test
    fun hiddenPhoneTabStopsLifecycleAndBackHandlingThenResumes() {
        presented = true
        showHosts()
        rule.runOnIdle {
            assertNotSame(rootDispatcher, pageDispatcher)
            assertEquals(Lifecycle.State.RESUMED, pageLifecycle.currentState)
        }
        pressBack()
        rule.runOnIdle {
            assertEquals(listOf("mine"), backEvents)
            presented = false
        }
        pressBack()
        rule.runOnIdle {
            assertEquals(listOf("mine", "browse"), backEvents)
            assertTrue(pageLifecycle.currentState <= Lifecycle.State.CREATED)
            assertTrue(rootDispatcher.isEnabled)
            presented = true
        }
        pressBack()
        rule.runOnIdle {
            assertEquals(listOf("mine", "browse", "mine"), backEvents)
            assertEquals(Lifecycle.State.RESUMED, pageLifecycle.currentState)
            assertEquals(1, created)
        }
    }

    @Test
    fun visibleAccountMovesToTheWideModalAndBackWithoutRecreatingItsContent() {
        presented = true
        showHosts()
        lateinit var oldIdentity: Any
        rule.runOnIdle {
            oldIdentity = checkNotNull(identity)
            selectPage("settings")
        }
        scrollPage()
        val anchor = pageScroll.firstVisibleItemIndex to pageScroll.firstVisibleItemScrollOffset
        rule.runOnIdle { phone = false }
        rule.onNodeWithTag("wide-account").assertIsDisplayed()
        rule.onNodeWithText("settings").assertIsDisplayed()
        pressBack()
        rule.runOnIdle {
            assertEquals(0, disposed)
            assertEquals(1, created)
            assertSame(oldIdentity, identity)
            assertEquals(anchor, pageScroll.firstVisibleItemIndex to pageScroll.firstVisibleItemScrollOffset)
            assertEquals(listOf("mine"), backEvents)
            presented = false
        }
        rule.onNodeWithTag("wide-account").assertDoesNotExist()
        rule.onNodeWithTag("account-content", useUnmergedTree = true).assertExists().assertIsNotDisplayed()
        pressBack()
        rule.runOnIdle {
            assertEquals(listOf("mine", "browse"), backEvents)
            assertTrue(pageLifecycle.currentState <= Lifecycle.State.CREATED)
        }
        rule.runOnIdle { presented = true }
        rule.onNodeWithTag("wide-account").assertIsDisplayed()
        rule.runOnIdle { assertEquals(1, created); phone = true }
        rule.onNodeWithTag("wide-account").assertDoesNotExist()
        rule.onNodeWithTag("account-content").assertIsDisplayed()
        rule.onNodeWithText("settings").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(1, created)
            assertEquals(0, disposed)
            assertSame(oldIdentity, identity)
            assertEquals(anchor, pageScroll.firstVisibleItemIndex to pageScroll.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun hiddenAccountKeepsItsContentAndScrollAcrossWideAndPhoneLayouts() {
        presented = true
        showHosts()
        lateinit var original: Any
        rule.runOnIdle {
            original = checkNotNull(identity)
            selectPage("settings")
        }
        scrollPage()
        val anchor = pageScroll.firstVisibleItemIndex to pageScroll.firstVisibleItemScrollOffset
        rule.runOnIdle { presented = false }
        rule.runOnIdle { phone = false }
        rule.onNodeWithTag("wide-account").assertDoesNotExist()
        rule.onNodeWithTag("account-content", useUnmergedTree = true).assertExists().assertIsNotDisplayed()
        pressBack()
        rule.runOnIdle {
            assertSame(original, identity)
            assertEquals(0, disposed)
            assertTrue(pageLifecycle.currentState <= Lifecycle.State.CREATED)
            assertEquals(listOf("browse"), backEvents)
            phone = true
        }
        rule.runOnIdle { presented = true }
        rule.onNodeWithText("settings").assertIsDisplayed()
        rule.runOnIdle {
            assertSame(original, identity)
            assertEquals(1, created)
            assertEquals(0, disposed)
            assertEquals(anchor, pageScroll.firstVisibleItemIndex to pageScroll.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun sessionResetDisposesHiddenContentAndResetsTheLazyVisit() {
        presented = true
        showHosts()
        lateinit var oldIdentity: Any
        lateinit var oldLifecycle: Lifecycle
        rule.runOnIdle {
            oldIdentity = checkNotNull(identity)
            oldLifecycle = pageLifecycle
            selectPage("settings")
            presented = false
        }
        rule.runOnIdle { session++ }
        rule.onNodeWithTag("account-content", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle {
            assertEquals(1, created)
            assertEquals(1, disposed)
            assertNull(identity)
            assertEquals(Lifecycle.State.DESTROYED, oldLifecycle.currentState)
            presented = true
        }
        rule.onNodeWithText("profile").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(2, created)
            assertNotSame(oldIdentity, identity)
        }
    }

    private fun pressBack() {
        rule.runOnIdle { DirectNavigationEventInputHandler(rootDispatcher).handleOnCompleted() }
        rule.waitForIdle()
    }

    private fun scrollPage() {
        rule.runOnIdle { pageScope.launch { pageScroll.scrollToItem(20, 7) } }
        rule.waitForIdle()
    }

    private fun showHosts() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DesktopNavigationOwner {
                    rootDispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
                    MaFeiBackHandler(true) { backEvents += "browse" }
                    Box(Modifier.size(420.dp, 640.dp).testTag("stage")) {
                        key(session) {
                            // The production caller moves one content tree between these two
                            // slots. A plain lambda would recreate its remember state on a move.
                            val accountContent = remember { movableContentOf<Modifier> { modifier ->
                                val token = remember { Any() }
                                var page by remember { mutableStateOf("profile") }
                                val scroll = rememberLazyListState()
                                val scope = rememberCoroutineScope()
                                val lifecycle = LocalLifecycleOwner.current.lifecycle
                                val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
                                SideEffect {
                                    identity = token
                                    pageLifecycle = lifecycle
                                    pageDispatcher = dispatcher
                                    pageScroll = scroll
                                    pageScope = scope
                                    selectPage = { page = it }
                                }
                                DisposableEffect(token) {
                                    created++
                                    onDispose {
                                        disposed++
                                        if (identity === token) identity = null
                                    }
                                }
                                MaFeiBackHandler(true) { backEvents += "mine" }
                                Column(modifier.testTag("account-content").drawBehind { draws++ }) {
                                    BasicText(page)
                                    LazyColumn(state = scroll) {
                                        items(60) { BasicText("Row $it", Modifier.height(40.dp)) }
                                    }
                                }
                            } }
                            RetainedPhoneAccountHost(phone, presented, accountContent)
                            // The same condition and movable content as BrowseAccountModal;
                            // no second account tree is ever instantiated beside the modal.
                            if (!phone && presented) {
                                Box(Modifier.fillMaxSize().testTag("wide-account")) {
                                    accountContent(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }
}
