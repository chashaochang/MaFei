package cn.mafei.cmp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.ui.MaFeiRetainedContent
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.ServerManagement
import cn.mafei.cmp.data.repositories.MediaRepository
import cn.mafei.cmp.session.RecordingAdapter
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ManagementRouteActiveTest {
    @get:org.junit.Rule val rule = createComposeRule()

    @Test fun retainedHiddenManagementStopsPollingAndRestartsOnlyWhenVisible() {
        val active = mutableStateOf(false)
        val api = ManagementApi()
        val adapter = object : MediaProviderAdapter by RecordingAdapter() { override val management: ServerManagement = api }
        val repository = MediaRepository(FixedEpochClock())
        val session = runBlocking { repository.open(adapter) }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaFeiTheme {
                Box(Modifier.size(390.dp, 844.dp)) {
                    MaFeiRetainedContent(active.value) {
                        ManagementRoute(repository, session, taskId = null, onBack = {}, onTask = {})
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(5_000)
        assertEquals(0, api.sessionReads.get())
        rule.runOnIdle { active.value = true }
        rule.mainClock.advanceTimeBy(48)
        rule.waitUntil(5_000) { api.sessionReads.get() > 0 }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(4_000)
        rule.waitUntil(5_000) { api.sessionReads.get() > 1 }
        rule.runOnIdle { active.value = false }
        rule.mainClock.advanceTimeBy(48)
        rule.waitForIdle()
        val paused = api.sessionReads.get()
        rule.mainClock.advanceTimeBy(10_000)
        assertEquals(paused, api.sessionReads.get())
        rule.runOnIdle { active.value = true }
        rule.mainClock.advanceTimeBy(48)
        rule.waitUntil(5_000) { api.sessionReads.get() > paused }
    }

    private class ManagementApi : ServerManagement {
        val sessionReads = AtomicInteger()
        override val currentDeviceId = "device"
        override suspend fun isAdministrator() = true
        override suspend fun server() = ManagedServer("Server", "10.10", "Jellyfin", "Linux", "https://fixture", true, false)
        override suspend fun counts() = ManagedMediaCounts(1, 2, 3, 6)
        override suspend fun users() = emptyList<ManagedUser>()
        override suspend fun tasks() = emptyList<ManagedTask>()
        override suspend fun sessions(nowEpochMs: Long): List<ManagedSession> { sessionReads.incrementAndGet(); return emptyList() }
        override suspend fun activity(offset: Int, limit: Int, minDate: String?, hasUserId: Boolean?) = ManagedActivityPage(emptyList(), 0, offset)
        override suspend fun sessionCommand(id: String, command: ManagedSessionCommand) = Unit
        override suspend fun sessionMessage(id: String, text: String) = Unit
        override suspend fun startLibraryScan() = LibraryScanResult.Started
        override suspend fun startTask(id: String) = Unit
        override suspend fun cancelTask(id: String) = Unit
        override suspend fun devices() = emptyList<ManagedDevice>()
        override suspend fun renameDevice(id: String, customName: String) = Unit
        override suspend fun revokeDevice(id: String) = Unit
    }
}
