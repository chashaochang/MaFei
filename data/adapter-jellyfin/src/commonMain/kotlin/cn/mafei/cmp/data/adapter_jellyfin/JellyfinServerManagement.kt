package cn.mafei.cmp.data.adapter_jellyfin

import cn.mafei.cmp.core.domain.*
import cn.mafei.cmp.data.adapter_contract.ServerManagement
import cn.mafei.cmp.providers.jellyfin.core.JellyfinError
import cn.mafei.cmp.providers.jellyfin.facade.JellyfinFacade
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

internal class JellyfinServerManagement(private val facade: JellyfinFacade) : ServerManagement {
    override val currentDeviceId get() = facade.client.config.deviceId
    private val actions = Mutex()
    private suspend fun <T> call(block: suspend () -> T): T = try { block() }
        catch (error: JellyfinError) { throw error.toDomainError() }
    override suspend fun isAdministrator() = call {
        facade.management.currentUser().obj("Policy").bool("IsAdministrator")
    }
    override suspend fun server() = call {
        val value = facade.management.server()
        ManagedServer(value.text("ServerName"), value.text("Version"), value.text("ProductName"),
            value.text("OperatingSystemDisplayName").ifBlank { value.text("OperatingSystem") },
            facade.client.config.baseUrl.toEncodedString(), !value.bool("IsShuttingDown"), value.bool("HasPendingRestart"))
    }
    override suspend fun counts() = call {
        val value = facade.management.counts()
        val movies = value.count("MovieCount"); val series = value.count("SeriesCount"); val episodes = value.count("EpisodeCount")
        ManagedMediaCounts(movies, series, episodes, value.count("ItemCount").takeIf { it != 0 } ?: (movies + series + episodes))
    }
    override suspend fun users() = call {
        facade.management.users().map { ManagedUser(it.text("Id"), it.text("Name"),
            it.obj("Policy").bool("IsAdministrator"), it.obj("Policy").bool("IsDisabled")) }
    }
    override suspend fun tasks() = call { facade.management.tasks().map(::mapTask) }
    override suspend fun devices() = call {
        facade.management.devices().map { value -> ManagedDevice(value.text("Id"), value.text("Name"),
            value.text("CustomName"), value.text("AppName"), value.text("AppVersion"), value.text("LastUserId"),
            value.text("LastUserName"), value.text("DateLastActivity"),
            value.text("Id").isNotBlank() && value.text("Id") == facade.client.config.deviceId) }
    }
    override suspend fun renameDevice(id: String, customName: String): Unit = actions.withLock {
        val current = devices().firstOrNull { it.id == id && id.isNotBlank() }
            ?: throw ResourceNotFoundError("Device is unavailable")
        val name = customName.trim()
        if (name != current.displayName) call { facade.management.renameDevice(id, name) }
    }
    override suspend fun revokeDevice(id: String): Unit = actions.withLock { call { facade.management.revokeDevice(id) } }
    override suspend fun sessions(nowEpochMs: Long) = call {
        facade.management.sessions().filter {
            val activeAt = cn.mafei.cmp.core.serialization.parseIsoInstantToEpochMs(it.text("LastActivityDate"))
            activeAt != null && nowEpochMs - activeAt <= 95_000 &&
                (it["NowPlayingItem"] is JsonObject || it.text("UserId").isNotBlank())
        }.map(::mapSession)
    }
    override suspend fun activity(offset: Int, limit: Int, minDate: String?, hasUserId: Boolean?) = call {
        val document = facade.management.activity(offset, limit, minDate, hasUserId)
        val names = users().associate { it.id to it.name }
        val items = (document["Items"] as? JsonArray)?.map { element ->
            val value = element as? JsonObject ?: throw ProtocolDecodingError("Invalid activity entry")
            val short = value.text("ShortOverview").ifBlank { value.text("Overview") }
            ManagedActivity(value.number("Id"), value.text("Date"), value.text("Severity").ifBlank { "Information" },
                names[value.text("UserId")].orEmpty(), value.text("Name"), short,
                value.text("Overview").ifBlank { short }, value.text("Type"), value.text("ItemId"))
        }.orEmpty()
        ManagedActivityPage(items, document.count("TotalRecordCount"), (document["StartIndex"] as? JsonPrimitive)?.intOrNull ?: offset)
    }
    override suspend fun sessionCommand(id: String, command: ManagedSessionCommand) = actions.withLock {
        requireControllable(id)
        call { facade.management.sessionCommand(id, command.name) }
    }
    override suspend fun sessionMessage(id: String, text: String) = actions.withLock {
        requireControllable(id)
        call { facade.management.sessionMessage(id, text.trim()) }
    }
    private suspend fun requireControllable(id: String) {
        val found = call { facade.management.sessions().firstOrNull { it.text("Id") == id && id.isNotBlank() } }
            ?: throw ResourceNotFoundError("Session is unavailable")
        if (!mapSession(found).controllable) throw AuthorizationError("Session does not support remote control")
    }
    override suspend fun startLibraryScan(): LibraryScanResult = actions.withLock {
        // Legacy scan finds only RefreshLibrary and never starts an arbitrary idle task.
        val scan = tasks().firstOrNull { it.key == "RefreshLibrary" && it.id.isNotBlank() }
            ?: return@withLock LibraryScanResult.Unavailable
        if (scan.busy) return@withLock LibraryScanResult.AlreadyRunning
        if (scan.state != "Idle") return@withLock LibraryScanResult.Unavailable
        call { facade.management.startTask(scan.id) }
        LibraryScanResult.Started
    }
    override suspend fun startTask(id: String) = actions.withLock {
        val task = tasks().firstOrNull { it.id == id && id.isNotBlank() } ?: throw ResourceNotFoundError("Task is unavailable")
        if (task.busy) throw ClientConfigError("Task is already running")
        call { facade.management.startTask(id) }
    }
    override suspend fun cancelTask(id: String) = actions.withLock {
        val task = tasks().firstOrNull { it.id == id && id.isNotBlank() } ?: throw ResourceNotFoundError("Task is unavailable")
        if (!task.busy) throw ClientConfigError("Task is not running")
        call { facade.management.cancelTask(id) }
    }
}

internal fun mapTask(value: JsonObject): ManagedTask {
    val last = value.obj("LastExecutionResult")
    val raw = (value["CurrentProgressPercentage"] as? JsonPrimitive)?.doubleOrNull ?: 0.0
    return ManagedTask(value.text("Id"), value.text("Key"), value.text("Name"), value.text("Description"),
        value.text("Category"), value.text("State"), if (raw.isFinite()) raw.coerceIn(0.0, 100.0) else 0.0,
        value.bool("IsHidden"), last.text("StartTimeUtc"), last.text("EndTimeUtc"), last.text("Status"),
        last.text("ErrorMessage").ifBlank { last.text("LongErrorMessage") })
}
private fun JsonObject.text(name: String) = (get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.bool(name: String) = (get(name) as? JsonPrimitive)?.booleanOrNull == true
private fun JsonObject.count(name: String) = ((get(name) as? JsonPrimitive)?.intOrNull ?: 0).coerceAtLeast(0)
private fun JsonObject.obj(name: String) = get(name) as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.number(name: String) = (get(name) as? JsonPrimitive)?.longOrNull ?: 0

internal fun mapSession(value: JsonObject): ManagedSession {
    val media = value.obj("NowPlayingItem"); val play = value.obj("PlayState"); val transcode = value.obj("TranscodingInfo")
    val subtitle = if (media.text("SeriesName").isNotBlank()) buildList {
        add(media.text("SeriesName"))
        val season = (media["ParentIndexNumber"] as? JsonPrimitive)?.intOrNull
        val episode = (media["IndexNumber"] as? JsonPrimitive)?.intOrNull
        if (season != null && episode != null) add("S$season E$episode")
    }.joinToString(" · ") else media.text("Album").ifBlank {
        (media["Artists"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString(", ").orEmpty()
    }
    val kind = when {
        transcode.isNotEmpty() && (transcode.bool("IsVideoDirect") || transcode.text("VideoCodec").isBlank()) && transcode.bool("IsAudioDirect") -> ManagedPlaybackKind.Remux
        transcode.bool("IsVideoDirect") -> ManagedPlaybackKind.DirectStream
        play.text("PlayMethod") == "Transcode" -> ManagedPlaybackKind.Transcode
        play.text("PlayMethod") in setOf("DirectStream", "DirectPlay") -> ManagedPlaybackKind.DirectPlay
        else -> ManagedPlaybackKind.Idle
    }
    return ManagedSession(value.text("Id"), value.text("ServerId"), value.text("UserName"),
        value.text("DeviceName").ifBlank { value.text("DeviceType") }, value.text("DeviceType"), value.text("Client"),
        value.text("ApplicationVersion"), value.text("RemoteEndPoint"), value.text("LastActivityDate"),
        value.bool("SupportsMediaControl"), value.bool("SupportsRemoteControl"), media.text("Id"), media.text("Name"), subtitle,
        (play.number("PositionTicks") / 10_000).coerceAtLeast(0), (media.number("RunTimeTicks") / 10_000).coerceAtLeast(0),
        play.bool("IsPaused"), kind, transcode.number("Bitrate"), (transcode["Framerate"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        transcode.text("Container"), transcode.text("VideoCodec"), transcode.text("AudioCodec"),
        if (transcode.number("Width") > 0 && transcode.number("Height") > 0) "${transcode.number("Width")}x${transcode.number("Height")}" else "",
        (transcode["TranscodeReasons"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty())
}
