package cn.mafei.cmp.session

import cn.mafei.cmp.core.domain.AccountId
import cn.mafei.cmp.core.domain.CollectionId
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import cn.mafei.cmp.data.adapter_contract.AdapterCapabilities
import cn.mafei.cmp.data.adapter_contract.MediaProviderAdapter
import cn.mafei.cmp.data.adapter_contract.PlaybackEvent
import cn.mafei.cmp.data.adapter_contract.PlaybackPreparation
import cn.mafei.cmp.core.domain.Page
import cn.mafei.cmp.core.domain.PageRequest
import cn.mafei.cmp.player.api.PlayableResource
import cn.mafei.cmp.player.api.PlaybackGeneration
import cn.mafei.cmp.player.api.PlayerCapabilities
import cn.mafei.cmp.player.api.PlayerRuntime
import cn.mafei.cmp.player.api.RuntimeCommand
import cn.mafei.cmp.player.api.RuntimeEvent

/** Recording adapter for composition tests; all non-playback methods throw. */
internal class RecordingAdapter : MediaProviderAdapter {
    val reports = mutableListOf<Pair<PlaybackPreparation.Ready, PlaybackEvent>>()
    override suspend fun preparePlayback(item: MediaId): PlaybackPreparation =
        PlaybackPreparation.Ready(
            streamUrl = "https://stream.test/s",
            reportingContext = "{\"item\":\"${item.value}\"}",
        )

    override suspend fun reportPlayback(stream: PlaybackPreparation.Ready, event: PlaybackEvent) {
        reports += stream to event
    }

    override val provider = ProviderKind.Jellyfin
    override val serverId = ServerId("srv")
    override val accountId = AccountId("acc")
    override val capabilities = AdapterCapabilities(playbackReporting = true)
    override suspend fun collections(request: PageRequest) = throw NotImplementedError()
    override suspend fun items(collectionId: CollectionId?, request: PageRequest) = throw NotImplementedError()
    override suspend fun search(term: String, request: PageRequest) = throw NotImplementedError()
    override suspend fun item(id: MediaId) = throw NotImplementedError()
    override suspend fun seasons(seriesId: MediaId): Page<cn.mafei.cmp.core.domain.MediaItem> = throw NotImplementedError()
    override suspend fun episodes(seriesId: MediaId, seasonId: MediaId?, request: PageRequest) = throw NotImplementedError()
    override suspend fun setFavorite(id: MediaId, favorite: Boolean) = throw NotImplementedError()
    override suspend fun setWatched(id: MediaId, watched: Boolean) = throw NotImplementedError()
}

/** Engine fake that records Load resources and can emit Ready/Playing events. */
internal class CountingRuntime : PlayerRuntime {
    var lastLoad: PlayableResource? = null
    val commands = mutableListOf<RuntimeCommand>()
    private var sink: ((RuntimeEvent) -> Unit)? = null
    override val capabilities = PlayerCapabilities()
    override fun bind(eventSink: (RuntimeEvent) -> Unit) { sink = eventSink }
    override fun execute(command: RuntimeCommand) {
        commands += command
        if (command is RuntimeCommand.Load) lastLoad = command.resource
    }

    fun emitReady(generation: PlaybackGeneration) { sink?.invoke(RuntimeEvent.Ready(generation, 60_000)) }
    fun emitPlaying(generation: PlaybackGeneration) { sink?.invoke(RuntimeEvent.Playing(generation)) }
}
