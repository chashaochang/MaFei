package cn.mafei.cmp.session

import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.player.api.*
import kotlinx.coroutines.*

/** Apple AVPlayer exposes muxed subtitles natively; sidecar text is synchronized by this wrapper. */
internal class ExternalSubtitlePlayerRuntime(
    private val delegate: PlayerRuntime,
    private val transport: HttpTransport,
    private val scope: CoroutineScope,
) : PlayerRuntime by delegate {
    private var sink: ((RuntimeEvent) -> Unit)? = null
    private var generation: PlaybackGeneration? = null
    private var pending: Job? = null
    private var timeline: SubtitleTimeline? = null
    private var positionMs = 0L
    private var displayed = ""

    override fun bind(eventSink: (RuntimeEvent) -> Unit) {
        sink = eventSink
        delegate.bind { event -> scope.launch {
            val currentSink = sink ?: return@launch
            currentSink(event)
            if (event.generation === generation) {
                if (event is RuntimeEvent.Progress) { positionMs = event.positionMs; publish() }
                if (event is RuntimeEvent.Ended || event is RuntimeEvent.Failed) reset()
            }
        } }
    }

    override fun execute(command: RuntimeCommand) {
        when (command) {
            is RuntimeCommand.Load -> {
                reset(); generation = command.generation; positionMs = command.startPositionMs
                val url = command.resource.subtitleUrl
                if (url != null) pending = scope.launch {
                    try {
                        val source = HttpUrl.parse(command.resource.url)
                        val target = HttpUrl.parse(url)
                        val sameOrigin = source.scheme == target.scheme && source.host == target.host && source.port == target.port
                        val request = HttpRequest.builder(HttpMethod.Get, target)
                            .redirectPolicy(RedirectPolicy.FollowSameOrigin)
                            .readPolicy(BodyReadPolicy.BufferedAtMost(4 * 1024 * 1024L)).timeoutMillis(15_000)
                        if (sameOrigin) command.resource.headers.forEach { (name, value) -> request.header(name, value) }
                        val response = transport.execute(request.build())
                        if (response.statusCode !in 200..299) return@launch
                        val decoded = withContext(Dispatchers.Default) { SubtitleTimeline(ExternalSubtitleParser.parse(response.bodyText())) }
                        ensureActive()
                        if (generation === command.generation) { timeline = decoded; publish() }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* A missing optional subtitle must never stop the video. */ }
                }
            }
            is RuntimeCommand.Seek -> if (command.generation === generation) { positionMs = command.positionMs; publish() }
            is RuntimeCommand.Stop -> if (command.generation === generation) reset()
            RuntimeCommand.Release -> { reset(); sink = null }
            else -> Unit
        }
        delegate.execute(command)
    }

    private fun publish() {
        val current = generation ?: return
        val text = timeline?.textAt(positionMs).orEmpty()
        if (displayed != text) { displayed = text; sink?.invoke(RuntimeEvent.Subtitle(current, text)) }
    }
    private fun reset() {
        pending?.cancel(); pending = null; timeline = null
        if (displayed.isNotEmpty()) generation?.let { sink?.invoke(RuntimeEvent.Subtitle(it, "")) }
        displayed = ""; generation = null
    }
}
