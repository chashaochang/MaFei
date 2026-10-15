package cn.mafei.cmp.session

import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.player.api.*
import kotlinx.coroutines.*
import kotlin.test.*

class ExternalSubtitlePlayerRuntimeTest {
    private class Engine : PlayerRuntime {
        override val capabilities = PlayerCapabilities()
        lateinit var emit: (RuntimeEvent) -> Unit
        val commands = mutableListOf<RuntimeCommand>()
        override fun bind(eventSink: (RuntimeEvent) -> Unit) { emit = eventSink }
        override fun execute(command: RuntimeCommand) { commands += command }
    }
    private val srt = "1\n00:00:01,000 --> 00:00:03,000\n字幕"

    @Test fun followsPlaybackClockSeekAndSubtitleOff(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val engine = Engine(); val events = mutableListOf<RuntimeEvent>()
            val http = object : HttpTransport {
                override suspend fun execute(request: HttpRequest) = HttpResponse(200, HttpHeaders.EMPTY, srt.encodeToByteArray(), request.url, request.url)
            }
            val runtime = ExternalSubtitlePlayerRuntime(engine, http, scope).also { it.bind(events::add) }
            val generation = PlaybackGeneration()
            runtime.execute(RuntimeCommand.Load(generation, PlayableResource("one", "https://media.test/video", subtitleUrl = "https://media.test/subtitle"), 1500))
            withTimeout(2000) { while (events.filterIsInstance<RuntimeEvent.Subtitle>().none { it.text == "字幕" }) delay(5) }
            engine.emit(RuntimeEvent.Progress(generation, 3500, 4000))
            assertEquals("", events.filterIsInstance<RuntimeEvent.Subtitle>().last().text)
            runtime.execute(RuntimeCommand.Seek(generation, 1500))
            assertEquals("字幕", events.filterIsInstance<RuntimeEvent.Subtitle>().last().text)
            val next = PlaybackGeneration()
            runtime.execute(RuntimeCommand.Load(next, PlayableResource("one", "https://media.test/video"), 1500))
            assertEquals("", events.filterIsInstance<RuntimeEvent.Subtitle>().last().text)
            engine.emit(RuntimeEvent.Progress(generation, 1500, 2000))
            assertEquals("", events.filterIsInstance<RuntimeEvent.Subtitle>().last().text)
            runtime.execute(RuntimeCommand.Release)
        } finally { scope.cancel() }
    }

    @Test fun authenticationIsNotForwardedToAnUnrelatedSubtitleHost(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requests = mutableListOf<HttpRequest>()
            val http = object : HttpTransport {
                override suspend fun execute(request: HttpRequest): HttpResponse {
                    requests += request
                    return HttpResponse(401, HttpHeaders.EMPTY, byteArrayOf(), request.url, request.url)
                }
            }
            val engine = Engine(); val events = mutableListOf<RuntimeEvent>()
            val runtime = ExternalSubtitlePlayerRuntime(engine, http, scope).also { it.bind(events::add) }
            val resource = PlayableResource("one", "https://media.test/video", mapOf("Cookie" to "fixture"), "https://other.test/subtitle")
            runtime.execute(RuntimeCommand.Load(PlaybackGeneration(), resource, 0))
            withTimeout(2000) { while (requests.isEmpty()) delay(5) }
            assertNull(requests.single().headers.first("Cookie"))
            assertTrue(events.none { it is RuntimeEvent.Failed })
            assertTrue(engine.commands.any { it is RuntimeCommand.Load })
            runtime.execute(RuntimeCommand.Release)
        } finally { scope.cancel() }
    }
}
