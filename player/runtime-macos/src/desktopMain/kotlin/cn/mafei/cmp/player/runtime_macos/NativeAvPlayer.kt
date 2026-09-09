package cn.mafei.cmp.player.runtime_macos

import java.nio.file.Files

/**
 * JNI surface of `libmafei_avplayer.dylib` (built from
 * `src/nativeMain/objc/MaFeiAVPlayer.m` by the Gradle task in this module).
 * Only the bundled binary is trusted; nothing is searched on the library path.
 */
internal object NativeAvPlayer {
    external fun create(): Long
    external fun loadUrl(handle: Long, url: String)
    external fun play(handle: Long)
    external fun pause(handle: Long)
    external fun stop(handle: Long)
    external fun seek(handle: Long, positionMs: Long)
    external fun setSpeed(handle: Long, speed: Float)
    external fun attachLayer(handle: Long, layerPointer: Long)
    external fun detachLayer(handle: Long)
    external fun positionMs(handle: Long): Long
    external fun durationMs(handle: Long): Long
    external fun bufferedMs(handle: Long): Long
    external fun timeControl(handle: Long): Int
    external fun itemStatus(handle: Long): Int
    external fun errorKind(handle: Long): Int
    external fun releasePlayer(handle: Long)

    private val loaded: Unit by lazy {
        if (!System.getProperty("os.name").startsWith("Mac") || System.getProperty("os.arch") !in setOf("aarch64", "arm64")) {
            throw IllegalStateException("AVPlayer JNI supports macOS Apple Silicon only")
        }
        val resource = NativeAvPlayer::class.java.getResourceAsStream("/native/macos-arm64/libmafei_avplayer.dylib")
            ?: throw IllegalStateException("Bundled AVPlayer JNI library missing")
        resource.use {
            val root = Files.createTempDirectory("mafei-avplayer-native-")
            root.toFile().deleteOnExit()
            val library = root.resolve("libmafei_avplayer.dylib")
            library.toFile().deleteOnExit()
            Files.copy(it, library)
            System.load(library.toAbsolutePath().toString())
        }
    }

    fun ensureLoaded() {
        loaded
    }
}
