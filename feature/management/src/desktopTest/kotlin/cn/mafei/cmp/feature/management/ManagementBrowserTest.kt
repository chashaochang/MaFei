package cn.mafei.cmp.feature.management

import cn.mafei.cmp.common.core.FixedEpochClock
import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.ServerManagement
import kotlinx.coroutines.*
import kotlin.test.*

class ManagementBrowserTest {
    private class Fake : ServerManagement {
        override val currentDeviceId = "fixture-device"
        var allowed = true; var serverFailure: Exception? = null; var scans = 0
        var deviceValues = emptyList<ManagedDevice>(); val revoked = mutableListOf<String>(); var failedDevice: String? = null
        var deviceFailure: Exception? = null
        override suspend fun isAdministrator() = allowed
        override suspend fun server(): ManagedServer {
            serverFailure?.let { throw it }
            return ManagedServer("Server", "10.10", "Jellyfin", "Linux", "https://fixture", true, false)
        }
        override suspend fun counts() = ManagedMediaCounts(1, 2, 3, 6)
        override suspend fun users() = listOf(ManagedUser("u", "User", true, false))
        override suspend fun tasks() = emptyList<ManagedTask>()
        override suspend fun sessions(nowEpochMs: Long) = emptyList<ManagedSession>()
        override suspend fun activity(offset: Int, limit: Int, minDate: String?, hasUserId: Boolean?) = ManagedActivityPage(emptyList(), 0, offset)
        override suspend fun startLibraryScan(): LibraryScanResult { scans++; return LibraryScanResult.Started }
        override suspend fun startTask(id: String) {}
        override suspend fun cancelTask(id: String) {}
        override suspend fun sessionCommand(id: String, command: ManagedSessionCommand) {}
        override suspend fun sessionMessage(id: String, text: String) {}
        override suspend fun devices(): List<ManagedDevice> { deviceFailure?.let { throw it }; return deviceValues }
        override suspend fun renameDevice(id: String, customName: String) {}
        override suspend fun revokeDevice(id: String) { if (id == failedDevice) throw NetworkUnreachableError(); revoked += id }
    }
    @Test fun oneFailedSectionDoesNotClearOtherSectionsOrLastSuccessfulData(): Unit = runBlocking {
        val fake = Fake(); val browser = ManagementBrowser(fake)
        browser.refresh(); fake.serverFailure = NetworkUnreachableError(); browser.refresh()
        assertEquals("Server", browser.state.value.server.value?.name)
        assertNotNull(browser.state.value.server.error)
        assertEquals(6, browser.state.value.counts.value?.total)
        assertNull(browser.state.value.counts.error)
    }
    @Test fun nonAdministratorNeverLoadsServerData(): Unit = runBlocking {
        val fake = Fake().also { it.allowed = false; it.serverFailure = AssertionErrorException() }
        val browser = ManagementBrowser(fake); browser.refresh(); browser.startScan()
        assertFalse(browser.state.value.allowed!!); assertNull(browser.state.value.server.value); assertEquals(0, fake.scans)
    }
    @Test fun scanSubmissionLockRetainsLegacyFifteenSecondWindow(): Unit = runBlocking {
        val fake = Fake(); val clock = FixedEpochClock(1000); val browser = ManagementBrowser(fake, clock)
        browser.startScan(); browser.startScan(); assertEquals(1, fake.scans)
        clock.advanceBy(15_001); browser.refreshTasks(); browser.startScan(); assertEquals(2, fake.scans)
    }
    @Test fun cancelledRefreshIsNotReportedAsAnEmptySuccess(): Unit = runBlocking {
        val fake = Fake().also { it.serverFailure = CancellationException("cancelled") }
        val browser = ManagementBrowser(fake)
        assertFailsWith<CancellationException> { browser.refresh() }
        assertNull(browser.state.value.server.error)
        assertNull(browser.state.value.server.value)
    }
    @Test fun devicesKeepLastResultOnRefreshFailureAndDoNotMasqueradeAsEmpty(): Unit = runBlocking {
        val fake = Fake().also { it.deviceValues = listOf(device("tv")) }; val browser = ManagementDevicesBrowser(fake)
        browser.refresh(); fake.deviceFailure = NetworkUnreachableError(); browser.refresh()
        assertEquals("tv", browser.state.value.devices?.single()?.id); assertNotNull(browser.state.value.error)
    }
    @Test fun deviceBatchExcludesCurrentAndReportsPartialFailure(): Unit = runBlocking {
        val fake = Fake().also { it.deviceValues = listOf(device("fixture-device", true), device("a"), device("b")); it.failedDevice = "b" }
        val browser = ManagementDevicesBrowser(fake); browser.refresh(); browser.revokeOthers()
        assertEquals(listOf("a"), fake.revoked); assertEquals("成功 1 · 失败 1", browser.state.value.message)
        assertFalse(browser.state.value.pending)
    }
    @Test fun deviceBatchUsesConfiguredIdentityEvenIfCurrentDeviceIsMissingFromResponse(): Unit = runBlocking {
        val fake = Fake().also { it.deviceValues = listOf(device("remote")) }
        ManagementDevicesBrowser(fake).revokeOthers(); assertEquals(listOf("remote"), fake.revoked)
    }
    @Test fun cancelledDeviceBatchIsNotReportedAsSucceeded(): Unit = runBlocking {
        val fake = Fake().also { it.deviceFailure = CancellationException() }; val browser = ManagementDevicesBrowser(fake)
        assertFailsWith<CancellationException> { browser.revokeOthers() }
        assertNull(browser.state.value.message); assertFalse(browser.state.value.pending)
    }
    private fun device(id: String, current: Boolean = false) = ManagedDevice(id, id, "", "", "", "", "", "", current)
    private class AssertionErrorException : Exception("must not request")
}
