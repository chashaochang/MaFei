package cn.mafei.cmp.player.api

/** Engine options applied at the next resource load, never by restarting active playback. */
data class PlayerPlaybackPreferences(
    val useHardwareDecode: Boolean = true,
    val cacheSizeMb: Int = 1024,
    val engine: PlayerEngineType = PlayerEngineType.Auto,
) {
    init { require(cacheSizeMb in CacheSizesMb) { "Unsupported player cache size" } }

    companion object {
        /** Matches the legacy PlayerEnginePage choices; this is a limit, not a preallocation. */
        val CacheSizesMb: List<Int> = listOf(512, 1024, 1536, 2048)
    }
}
