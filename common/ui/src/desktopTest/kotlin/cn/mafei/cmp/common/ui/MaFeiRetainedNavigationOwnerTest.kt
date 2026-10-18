package cn.mafei.cmp.common.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigationevent.DirectNavigationEventInputHandler
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule

class MaFeiRetainedNavigationOwnerTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun hiddenChildrenDoNotInterceptActiveSiblingOrRootAndCanResume() {
        val root = NavigationEventDispatcher()
        val rootOwner = owner(root)
        val input = DirectNavigationEventInputHandler(root)
        val events = mutableListOf<String>()
        var active by mutableStateOf<String?>("home")
        lateinit var home: NavigationEventDispatcher
        lateinit var library: NavigationEventDispatcher
        rule.setContent {
            DisposableEffect(root) { onDispose { root.dispose() } }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides rootOwner) {
                MaFeiBackHandler(true) { events += "root" }
                MaFeiRetainedNavigationOwner(active == "home") {
                    home = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
                    MaFeiBackHandler(true) { events += "home" }
                }
                // Registered last on purpose: a hidden child must not win callback precedence.
                MaFeiRetainedNavigationOwner(active == "library") {
                    library = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
                    MaFeiBackHandler(true) { events += "library" }
                }
            }
        }
        rule.runOnIdle {
            assertNotSame(root, home)
            assertNotSame(root, library)
            assertNotSame(home, library)
        }
        val originalHome = home
        val originalLibrary = library
        pressBack(input)
        rule.runOnIdle { assertEquals(listOf("home"), events) }

        rule.runOnIdle { active = "library" }
        pressBack(input)
        rule.runOnIdle { assertEquals(listOf("home", "library"), events) }

        rule.runOnIdle { active = null }
        pressBack(input)
        rule.runOnIdle { assertEquals(listOf("home", "library", "root"), events) }

        rule.runOnIdle { active = "home" }
        pressBack(input)
        rule.runOnIdle {
            assertEquals(listOf("home", "library", "root", "home"), events)
            assertSame(originalHome, home)
            assertSame(originalLibrary, library)
            assertTrue(root.isEnabled)
        }
    }

    @Test
    fun removingAChildDisposesOnlyThatDispatcherAndUnregistersItsHandlers() {
        val root = NavigationEventDispatcher()
        val rootOwner = owner(root)
        val input = DirectNavigationEventInputHandler(root)
        val events = mutableListOf<String>()
        var retained by mutableStateOf(true)
        lateinit var child: NavigationEventDispatcher
        rule.setContent {
            DisposableEffect(root) { onDispose { root.dispose() } }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides rootOwner) {
                MaFeiBackHandler(true) { events += "root" }
                if (retained) {
                    MaFeiRetainedNavigationOwner(true) {
                        child = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
                        MaFeiBackHandler(true) { events += "child" }
                    }
                }
            }
        }
        pressBack(input)
        rule.runOnIdle {
            assertEquals(listOf("child"), events)
            retained = false
        }
        pressBack(input)
        rule.runOnIdle {
            assertEquals(listOf("child", "root"), events)
            assertTrue(root.isEnabled)
            assertFailsWith<IllegalStateException> {
                DirectNavigationEventInputHandler(child).handleOnCompleted()
            }
        }
    }

    private fun pressBack(input: DirectNavigationEventInputHandler) {
        rule.runOnIdle { input.handleOnCompleted() }
        rule.waitForIdle()
    }

    private fun owner(dispatcher: NavigationEventDispatcher): NavigationEventDispatcherOwner =
        object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
}
