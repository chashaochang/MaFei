package cn.mafei.cmp.session

import cn.mafei.cmp.player.api.*
import cn.mafei.cmp.storage.DocumentSettingsStorage
import cn.mafei.cmp.storage.SettingsDocumentBackend
import cn.mafei.cmp.core.storage_api.SettingsStorage
import cn.mafei.cmp.core.storage_api.StorageUnavailableException
import kotlinx.coroutines.*
import kotlin.test.*

class PlayerPreferencesTest {
    private class Document : SettingsDocumentBackend {
        var bytes: ByteArray? = null
        var failWrites = false
        override fun read() = bytes?.copyOf()
        override fun replace(document: ByteArray) {
            check(!failWrites) { "disk unavailable" }
            bytes = document.copyOf()
        }
    }

    @Test fun defaultsMatchLegacyAndInvalidStorageIsBounded(): Unit = runBlocking {
        val settings = DocumentSettingsStorage(Document())
        val store = PlayerPreferencesStore(settings)
        assertEquals(PlayerPlaybackPreferences(true, 1024), store.load())
        for (raw in listOf("", "true:-1", "false:999999", "maybe:512", "true:512:extra")) {
            settings.putString(PlayerPreferencesStore.KEY, raw)
            assertEquals(PlayerPlaybackPreferences(), PlayerPreferencesStore(settings).load())
        }
        assertFailsWith<IllegalArgumentException> { PlayerPlaybackPreferences(cacheSizeMb = -1) }
    }

    @Test fun savesSurviveRecreationWithoutTouchingAccounts() = runBlocking {
        val document = Document()
        val settings = DocumentSettingsStorage(document)
        settings.putString("accounts.test", "unchanged")
        val store = PlayerPreferencesStore(settings)
        val selected = PlayerPlaybackPreferences(false, 1536)
        store.save(selected)
        assertEquals(selected, store.state.value)
        assertEquals(selected, PlayerPreferencesStore(DocumentSettingsStorage(document)).load())
        assertEquals("unchanged", settings.getString("accounts.test"))
        assertEquals(setOf("accounts.test", PlayerPreferencesStore.KEY), settings.listKeys().toSet())
    }

    @Test fun failedWriteKeepsPublishedAndPersistedSelection() = runBlocking {
        val document = Document()
        val store = PlayerPreferencesStore(DocumentSettingsStorage(document))
        val previous = PlayerPlaybackPreferences(false, 512)
        store.save(previous)
        document.failWrites = true
        assertFailsWith<StorageUnavailableException> { store.save(PlayerPlaybackPreferences(true, 2048)) }
        assertEquals(previous, store.state.value)
        assertEquals(previous, PlayerPreferencesStore(DocumentSettingsStorage(document)).load())
    }

    @Test fun leavingTheRouteDuringAWriteDoesNotSplitDiskAndRuntime(): Unit = runBlocking {
        val document = Document()
        val backend = DocumentSettingsStorage(document)
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val settings = object : SettingsStorage by backend {
            override suspend fun putString(key: String, value: String) {
                entered.complete(Unit)
                proceed.await()
                backend.putString(key, value)
            }
        }
        val store = PlayerPreferencesStore(settings)
        val selected = PlayerPlaybackPreferences(false, 2048)
        val job = launch { store.save(selected) }
        entered.await()
        job.cancel()
        proceed.complete(Unit)
        job.join()
        assertEquals(selected, store.state.value)
        assertEquals(selected, PlayerPreferencesStore(DocumentSettingsStorage(document)).load())
    }

    @Test fun aCancelledSaveDoesNotStartWriting(): Unit = runBlocking {
        val document = Document()
        val store = PlayerPreferencesStore(DocumentSettingsStorage(document))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            currentCoroutineContext().cancel()
            assertFailsWith<CancellationException> { store.save(PlayerPlaybackPreferences(false, 512)) }
        }
        job.join()
        assertNull(document.bytes)
    }

    @Test fun failedReloadPreservesTheLastSavedPreference(): Unit = runBlocking {
        val backend = DocumentSettingsStorage(Document())
        var failReads = false
        val store = PlayerPreferencesStore(object : SettingsStorage by backend {
            override suspend fun getString(key: String): String? {
                check(!failReads) { "read unavailable" }
                return backend.getString(key)
            }
        })
        val previous = PlayerPlaybackPreferences(false, 512)
        store.save(previous)
        failReads = true
        assertFailsWith<IllegalStateException> { store.load() }
        assertEquals(previous, store.state.value)
    }

    private class Engine(supported: Boolean) : PlayerRuntime {
        override val capabilities = PlayerCapabilities(playbackPreferences = supported)
        val log = mutableListOf<String>()
        val loads = mutableListOf<RuntimeCommand>()
        override fun bind(eventSink: (RuntimeEvent) -> Unit) = Unit
        override fun setPlaybackPreferences(preferences: PlayerPlaybackPreferences) {
            check(capabilities.playbackPreferences)
            log += "preferences:${preferences.useHardwareDecode}:${preferences.cacheSizeMb}"
        }
        override fun execute(command: RuntimeCommand) { log += "command"; loads += command }
        override fun setRenderMode(mode: PlayerRenderMode) { log += mode.name }
    }

    @Test fun appliesPreferencesOnlyOnLoadAndBeforeEngineReceivesIt() {
        val engine = Engine(true)
        var preferences = PlayerPlaybackPreferences()
        val runtime = PreferenceApplyingPlayerRuntime(engine) { preferences }
        val first = RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("1", "https://test/video"), 12345)
        runtime.execute(first)
        assertEquals(listOf("preferences:true:1024", "command"), engine.log)
        assertSame(first, engine.loads.single())
        preferences = PlayerPlaybackPreferences(false, 512)
        assertEquals(2, engine.log.size)
        runtime.setRenderMode(PlayerRenderMode.Direct)
        runtime.execute(RuntimeCommand.Seek(first.generation, 56789))
        assertEquals(listOf("preferences:true:1024", "command", "Direct", "command"), engine.log)
        runtime.execute(first.copy(generation = PlaybackGeneration()))
        assertEquals(listOf("preferences:false:512", "command"), engine.log.takeLast(2))
    }

    @Test fun unsupportedPlatformDoesNotReceiveMpvConfiguration() {
        val engine = Engine(false)
        val runtime = PreferenceApplyingPlayerRuntime(engine) { error("must not read unsupported preferences") }
        runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), PlayableResource("1", "https://test/video")))
        assertEquals(listOf("command"), engine.log)
    }
}
