package cn.mafei.cmp.storage

import java.awt.EventQueue
import kotlinx.coroutines.*
import kotlin.test.*

class DesktopMainDispatcherTest {
    @Test fun packagedDependencyGraphProvidesMainDispatcherOnAwtThread() = runBlocking<Unit> {
        withContext(Dispatchers.Main) { assertTrue(EventQueue.isDispatchThread()) }
    }
}
