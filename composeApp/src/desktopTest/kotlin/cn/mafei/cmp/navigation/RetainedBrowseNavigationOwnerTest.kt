@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package cn.mafei.cmp

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.LocalNavigationEventDispatcherOwner as NavDisplayNavigationOwner
import androidx.navigationevent.DirectNavigationEventInputHandler
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner as RootNavigationOwner
import cn.mafei.cmp.common.ui.MaFeiBackHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule

@OptIn(ExperimentalSharedTransitionApi::class)
class RetainedBrowseNavigationOwnerTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun navDisplayUsesItsChildAndHiddenStacksCannotConsumeBack() {
        val lifecycleOwner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
        }
        val events = mutableListOf<String>()
        var active by mutableStateOf<String?>("home")
        lateinit var root: NavigationEventDispatcher
        val children = mutableMapOf<String, NavigationEventDispatcher>()
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                DesktopNavigationOwner {
                    root = checkNotNull(RootNavigationOwner.current).navigationEventDispatcher
                    MaFeiBackHandler(true) { events += "root" }
                    for (name in listOf("home", "library")) {
                        RetainedBrowseNavigationOwner(active == name) {
                            val publicOwner = checkNotNull(RootNavigationOwner.current)
                            assertSame(publicOwner, NavDisplayNavigationOwner.current)
                            children[name] = publicOwner.navigationEventDispatcher
                            NavDisplay(
                                backStack = listOf("$name-root", "$name-detail"),
                                onBack = { events += name },
                                entryProvider = { route -> NavEntry(route) { BasicText(route) } },
                            )
                        }
                    }
                }
            }
        }
        rule.runOnIdle {
            assertNotSame(root, children.getValue("home"))
            assertNotSame(root, children.getValue("library"))
            assertNotSame(children.getValue("home"), children.getValue("library"))
        }
        val input = DirectNavigationEventInputHandler(root)
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
            assertTrue(root.isEnabled)
        }
    }

    private fun pressBack(input: DirectNavigationEventInputHandler) {
        rule.runOnIdle { input.handleOnCompleted() }
        rule.waitForIdle()
    }
}
