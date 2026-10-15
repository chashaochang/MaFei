package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.providers.jellyfin.core.*
import cn.mafei.cmp.providers.jellyfin.facade.DefaultJellyfinFacade
import cn.mafei.cmp.providers.jellyfin.testkit.FakeJellyfinTransport
import kotlinx.serialization.json.*
import kotlin.test.*

class JellyfinManagementTest {
    private fun adapter(http: FakeJellyfinTransport) = JellyfinMediaProviderAdapter(
        DefaultJellyfinFacade(JellyfinServerConfig(HttpUrl.parse("https://server.test/jellyfin"), "MaFei", "test", "id", "2"),
            http, InMemoryJellyfinSessionState(JellyfinSession("u", "fixture-token"))), ServerId("s"), AccountId("a"), "u").management

    @Test fun countAndServerFallbackFieldsMatchLegacy() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """{"MovieCount":2,"SeriesCount":3,"EpisodeCount":5,"ItemCount":0}""")
        assertEquals(ManagedMediaCounts(2, 3, 5, 10), api.counts())
        http.enqueueJson(200, """{"ServerName":"My server","OperatingSystem":"Linux","OperatingSystemDisplayName":"Ubuntu","HasPendingRestart":true}""")
        val server = api.server(); assertEquals("Ubuntu", server.operatingSystem); assertTrue(server.online); assertTrue(server.pendingRestart)
        assertEquals("/jellyfin/Items/Counts", http.requests.first().url.encodedPath)
        assertTrue(http.requests.all { it.headers.first("Authorization")?.contains("fixture-token") == true })
    }
    @Test fun busyScanNeverReplaysPost() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """[{"Id":"scan","Key":"RefreshLibrary","State":"Cancelling"}]""")
        assertEquals(LibraryScanResult.AlreadyRunning, api.startLibraryScan())
        assertEquals(1, http.requests.size)
    }
    @Test fun scanTargetsOnlyRefreshLibraryAndRechecksCurrentUser() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """[{"Id":"other","Key":"SomethingElse","State":"Idle"},{"Id":"scan/id","Key":"RefreshLibrary","State":"Idle"}]""")
        http.enqueueJson(200, """{"Policy":{"IsAdministrator":true}}""")
        http.enqueueJson(204, "")
        assertEquals(LibraryScanResult.Started, api.startLibraryScan())
        assertEquals("/jellyfin/Users/Me", http.requests[1].url.encodedPath)
        assertEquals("/jellyfin/ScheduledTasks/Running/scan%2Fid", http.requests.last().url.encodedPath)
        assertEquals(HttpMethod.Post, http.requests.last().method)
    }
    @Test fun permissionRevokedPreventsTaskMutation() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """[{"Id":"scan","State":"Running"}]""")
        http.enqueueJson(200, """{"Policy":{"IsAdministrator":false}}""")
        assertFailsWith<AuthorizationError> { api.cancelTask("scan") }
        assertTrue(http.requests.all { it.method == HttpMethod.Get })
    }
    @Test fun taskClampingAndErrorPrecedenceMatchLegacy() {
        val task = mapTask(Json.parseToJsonElement("""{"Id":"t","State":"Cancelling","CurrentProgressPercentage":220,"LastExecutionResult":{"ErrorMessage":"short","LongErrorMessage":"long"}}""").jsonObject)
        assertTrue(task.busy); assertFalse(task.running); assertEquals(100.0, task.progress); assertEquals("short", task.lastError)
    }
    @Test fun recentSessionsUseNinetyFiveSecondsAndOriginalDirectStreamMapping() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """[{"Id":"fresh","UserId":"u","LastActivityDate":"2026-10-10T00:00:00Z","PlayState":{"PlayMethod":"DirectStream","PositionTicks":12000000},"NowPlayingItem":{"Id":"video","RunTimeTicks":60000000,"SeriesName":"Series","ParentIndexNumber":1,"IndexNumber":2}},{"Id":"old","UserId":"u","LastActivityDate":"2026-10-09T23:58:00Z"},{"Id":"bad-date","UserId":"u","LastActivityDate":"n/a"}]""")
        val sessions = api.sessions(cn.mafei.cmp.core.serialization.parseIsoInstantToEpochMs("2026-10-10T00:00:10Z")!!)
        assertEquals(1, sessions.size); assertEquals(ManagedPlaybackKind.DirectPlay, sessions.single().playbackKind)
        assertEquals(1200L, sessions.single().positionMs); assertEquals("Series · S1 E2", sessions.single().itemSubtitle)
        assertTrue(http.requests.single().url.toEncodedString().contains("activeWithinSeconds=960"))
    }
    @Test fun dashboardActivityRetainsLegacyWindowAndUserNames() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """{"Items":[{"Id":1,"UserId":"u","Name":"Played","Overview":"full"}],"TotalRecordCount":1}""")
        http.enqueueJson(200, """[{"Id":"u","Name":"Alice"}]""")
        val page = api.activity(0, 7, "2026-10-09T00:00:00Z", true)
        assertEquals("Alice", page.items.single().userName); assertEquals("full", page.items.single().shortOverview)
        val url = http.requests.first().url.toEncodedString(); assertTrue(url.contains("Limit=7")); assertTrue(url.contains("HasUserId=true")); assertTrue(url.contains("MinDate="))
    }
    @Test fun devicesUseDeclaredQueryResultAndCurrentDeviceIdentity() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """{"Items":[{"Id":"id","Name":"Phone","CustomName":"My phone","AppName":"MaFei","AppVersion":"2","LastUserId":"u","LastUserName":"Alice","DateLastActivity":"2026-10-10T00:00:00Z"},{"Id":"remote","Name":"TV"}],"TotalRecordCount":2}""")
        val devices = api.devices()
        assertEquals("id", api.currentDeviceId); assertTrue(devices.first().current); assertFalse(devices.last().current)
        assertEquals("My phone", devices.first().displayName); assertEquals("Alice", devices.first().lastUserName)
        assertEquals("/jellyfin/Devices", http.requests.single().url.encodedPath)
    }
    @Test fun currentDeviceRevocationIsRejectedBeforeAnyNetworkMutation() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        assertFailsWith<AuthorizationError> { api.revokeDevice("id") }
        assertTrue(http.requests.isEmpty())
    }
    @Test fun renameRechecksPermissionAndUsesLegacyBodyAndIdQuery() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """{"Items":[{"Id":"device/id","Name":"TV"}]}""")
        http.enqueueJson(200, """{"Policy":{"IsAdministrator":true}}""")
        http.enqueueJson(204, "")
        api.renameDevice("device/id", "  Living room  ")
        val request = http.requests.last()
        assertEquals("/jellyfin/Devices/Options", request.url.encodedPath)
        assertTrue(request.url.toEncodedString().endsWith("id=device%2Fid"))
        assertEquals("Living room", Json.parseToJsonElement((request.body as HttpBody.Text).text).jsonObject["CustomName"]?.jsonPrimitive?.content)
    }
    @Test fun unchangedDeviceNameDoesNotSendAnUpdate() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """{"Items":[{"Id":"device","Name":"TV","CustomName":"Living room"}]}""")
        api.renameDevice("device", " Living room ")
        assertEquals(1, http.requests.size); assertEquals(HttpMethod.Get, http.requests.single().method)
    }
    @Test fun permissionRevokedPreventsDeviceRevoke() = runSuspendTest {
        val http = FakeJellyfinTransport(); val api = adapter(http)
        http.enqueueJson(200, """{"Policy":{"IsAdministrator":false}}""")
        assertFailsWith<AuthorizationError> { api.revokeDevice("remote") }
        assertTrue(http.requests.none { it.method == HttpMethod.Delete })
    }
}
