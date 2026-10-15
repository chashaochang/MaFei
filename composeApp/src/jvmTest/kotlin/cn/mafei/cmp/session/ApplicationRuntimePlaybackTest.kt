package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.Account
import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.Server
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.secure_credential_api.SecureCredentialStore
import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent
import cn.mafei.cmp.storage.DocumentSettingsStorage
import cn.mafei.cmp.storage.SettingsDocumentBackend
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private class Document : SettingsDocumentBackend {
    var bytes: ByteArray? = null
    override fun read() = bytes?.copyOf()
    override fun replace(document: ByteArray) { bytes = document.copyOf() }
}

private class Secrets : SecureCredentialStore {
    val values = mutableMapOf<CredentialAlias, SecureSecret>()
    override suspend fun save(alias: CredentialAlias, secret: SecureSecret, overwrite: Boolean) { values[alias] = secret }
    override suspend fun read(alias: CredentialAlias) = values[alias]
    override suspend fun exists(alias: CredentialAlias) = alias in values
    override suspend fun delete(alias: CredentialAlias) = values.remove(alias) != null
    override suspend fun listAliases(prefix: String?) = values.keys.filter { prefix == null || it.value.startsWith(prefix) }
    override suspend fun deleteAll(prefix: String?) = error("unscoped deletion")
}

private class StubTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = HttpResponse(
        200, HttpHeaders.EMPTY, ByteArray(0), request.url, request.url,
    )
    override fun close() { }
}

private class FakeEngine : PlayerRuntime {
    val released = mutableListOf<Int>()
    val commands = mutableListOf<RuntimeCommand>()
    private var sink: ((RuntimeEvent) -> Unit)? = null
    override val capabilities = PlayerCapabilities()
    override fun bind(eventSink: (RuntimeEvent) -> Unit) { sink = eventSink }
    override fun execute(command: RuntimeCommand) {
        commands += command
        if (command is RuntimeCommand.Load) sink?.invoke(RuntimeEvent.Ready(command.generation, 60_000))
        if (command is RuntimeCommand.Play) sink?.invoke(RuntimeEvent.Playing(command.generation))
        if (command is RuntimeCommand.Pause) sink?.invoke(RuntimeEvent.Paused(command.generation))
        if (command is RuntimeCommand.Release) markReleased() // the engine really relinquished
    }
    fun markReleased() { released += 1 }
}

private fun fixtureConnection(): cn.mafei.cmp.session.SavedConnection {
    val server = Server(ServerId("server"), ProviderKind.Jellyfin, "Fixture", "https://media.test/base")
    return cn.mafei.cmp.session.SavedConnection(
        server,
        Account(AccountId("a"), server.id, server.provider, "a"),
        "remote-a",
        "a".repeat(32),
    )
}

class ApplicationRuntimePlaybackTest {

    private val engine = FakeEngine()
    private val app = ApplicationRuntime(
        settings = DocumentSettingsStorage(Document()),
        secureStore = Secrets(),
        transport = StubTransport(),
        dispatcher = Dispatchers.Unconfined,
        playerRuntimeFactory = { engine },
    )

    private val factory = cn.mafei.cmp.session.DefaultProviderSessionFactory(StubTransport())

    private fun activateSession(): cn.mafei.cmp.session.SavedConnection {
        val connection = fixtureConnection()
        runBlocking {
            withTimeout(5_000) { app.sessions.activate(connection, bindingFor(connection)) }
        }
        return connection
    }

    private fun bindingFor(connection: cn.mafei.cmp.session.SavedConnection) =
        factory.restore(connection, mapOf(connection.tokenAlias to SecureSecret.AuthToken("MediaBrowser", "fixture")))

    @AfterTest
    fun tearDown() {
        runBlocking { app.close() }
    }

    @Test
    fun hostLifecyclePausesAndRestoresVideoWithoutDroppingTheAccount() = runBlocking<Unit> {
        activateSession()
        val session = checkNotNull(app.sessions.state.value.session)
        val playback = checkNotNull(app.playback(session))
        playback.controller.load(listOf(cn.mafei.cmp.player.api.PlayableResource("v", "https://media.test/v")))
        delay(50)
        app.setHostForeground(false)
        delay(50)
        assertTrue(engine.commands.any { it is RuntimeCommand.Pause })
        assertTrue(app.sessions.state.value.session === session)
        val previousPlayCount = engine.commands.count { it is RuntimeCommand.Play }
        app.setHostForeground(true)
        delay(50)
        assertEquals(previousPlayCount + 1, engine.commands.count { it is RuntimeCommand.Play })
        app.setHostForeground(true)
        delay(50)
        assertEquals(previousPlayCount + 1, engine.commands.count { it is RuntimeCommand.Play })
    }

    @Test
    fun anExplicitPauseIsNotUndoneOnForeground() = runBlocking<Unit> {
        activateSession()
        val playback = checkNotNull(app.playback(checkNotNull(app.sessions.state.value.session)))
        playback.controller.load(listOf(cn.mafei.cmp.player.api.PlayableResource("v", "https://media.test/v")))
        delay(50)
        playback.controller.pause()
        val plays = engine.commands.count { it is RuntimeCommand.Play }
        app.setHostForeground(false)
        app.setHostForeground(true)
        delay(50)
        assertEquals(plays, engine.commands.count { it is RuntimeCommand.Play })
    }

    @Test
    fun playbackReusesOneRuntimePerSessionAndReleasesOnLogout() = runBlocking<Unit> {
        val connection = activateSession()
        val session = app.sessions.state.value.session ?: throw AssertionError("no active session")
        val first = app.playback(session)
        assertTrue(first != null)
        assertTrue(first === app.playback(session)) // same session reuses the same runtime

        kotlinx.coroutines.withTimeout(5_000) { app.sessions.logout(session) }
        kotlinx.coroutines.delay(100) // observer (Unconfined) releases the retired playback

        // The retired playback was released: playing through it refuses.
        assertFailsWith<IllegalStateException> { first!!.play(MediaId("item-1")) }

        // Re-activating the same saved connection yields a fresh playback.
        val session2 = runBlocking {
            withTimeout(5_000) { app.sessions.activate(connection, bindingFor(connection)) }
        }
        val second = app.playback(session2)
        println("DBG second=$second firstHash=${System.identityHashCode(first)} secondHash=${System.identityHashCode(second)} stateSession=${app.sessions.state.value.session === session2} logoutsDone=true")
        assertTrue(second != null && second !== first)
        assertTrue(engine.released.size >= 1) // the retired engine was marked released
    }

    @Test
    fun playbackReturnsNullWithoutAFactory() = runBlocking<Unit> {
        val plain = ApplicationRuntime(
            settings = DocumentSettingsStorage(Document()),
            secureStore = Secrets(),
            transport = StubTransport(),
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            val connection = fixtureConnection()
            val session = runBlocking {
                withTimeout(5_000) { plain.sessions.activate(connection, bindingFor(connection)) }
            }
            // Explicit configuration gap, never a faked player.
            assertNull(plain.playback(session))
        } finally {
            plain.close()
        }
    }

    @Test
    fun closeReleasesAllPlaybacksAndRefusesFurtherAccess() = runBlocking<Unit> {
        val connection = activateSession()
        val session = app.sessions.state.value.session ?: throw AssertionError("no active session")
        assertTrue(app.playback(session) != null)
        app.close()
        assertTrue(engine.released.isNotEmpty()) // engine relinquished on close
        assertFailsWith<IllegalStateException> { app.playback(session) } // closed root refuses
        assertFailsWith<IllegalStateException> { app.connect(ProviderKind.Jellyfin, "https://media.test", "u", "p") }
    }

    private fun ApplicationRuntime.bindingFor(connection: cn.mafei.cmp.session.SavedConnection) =
        factory.restore(connection, mapOf(connection.tokenAlias to SecureSecret.AuthToken("MediaBrowser", "fixture")))
}
