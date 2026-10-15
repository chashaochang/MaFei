package cn.mafei.cmp.core.domain

data class ManagedServer(
    val name: String, val version: String, val productName: String,
    val operatingSystem: String, val address: String, val online: Boolean, val pendingRestart: Boolean,
)

data class ManagedMediaCounts(val movies: Int, val series: Int, val episodes: Int, val total: Int)
data class ManagedUser(val id: String, val name: String, val administrator: Boolean, val disabled: Boolean)
data class ManagedTask(
    val id: String, val key: String, val name: String, val description: String, val category: String,
    val state: String, val progress: Double, val hidden: Boolean,
    val lastStart: String, val lastEnd: String, val lastStatus: String, val lastError: String,
) {
    val running get() = state == "Running"
    val busy get() = running || state == "Cancelling"
}

enum class LibraryScanResult { Started, AlreadyRunning, Unavailable }

data class ManagedSession(
    val id: String, val serverId: String, val userName: String, val deviceName: String,
    val deviceType: String, val client: String, val version: String, val remoteAddress: String,
    val lastActivity: String, val mediaControl: Boolean, val remoteControl: Boolean,
    val itemId: String, val itemName: String, val itemSubtitle: String,
    val positionMs: Long, val durationMs: Long, val paused: Boolean,
    val playbackKind: ManagedPlaybackKind, val bitrate: Long, val framerate: Double,
    val container: String, val videoCodec: String, val audioCodec: String, val resolution: String,
    val transcodeReasons: List<String>,
) {
    val controllable get() = serverId.isNotBlank() && remoteControl
}
enum class ManagedPlaybackKind { Idle, DirectPlay, DirectStream, Remux, Transcode }
enum class ManagedSessionCommand { PlayPause, Stop }
data class ManagedActivity(
    val id: Long, val date: String, val severity: String, val userName: String, val name: String,
    val shortOverview: String, val overview: String, val type: String, val itemId: String,
)
data class ManagedActivityPage(val items: List<ManagedActivity>, val total: Int, val offset: Int)

data class ManagedDevice(
    val id: String, val name: String, val customName: String, val appName: String, val appVersion: String,
    val lastUserId: String, val lastUserName: String, val lastActivity: String, val current: Boolean,
) { val displayName get() = customName.ifBlank { name } }
