package cn.xiaobai.mafei.data.jellyfin

import android.content.Context
import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.storage.AndroidAppContextHolder
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo

internal class AndroidJellyfinProvider(
    private val appContext: Context = AndroidAppContextHolder.requireContext(),
    clientName: String = "MaFei",
    clientVersion: String = "0.1.0",
    deviceId: String = "mafei-device",
    deviceName: String = "MaFei Android",
) : JellyfinProvider {
    val jellyfin: Jellyfin = createJellyfin {
        AppLogger.info("Provider", "Initializing Jellyfin provider with Android context.")
        context = appContext
        clientInfo = ClientInfo(
            name = clientName,
            version = clientVersion,
        )
        deviceInfo = DeviceInfo(
            id = deviceId,
            name = deviceName,
        )
    }
}

internal fun JellyfinProvider.requireAndroidProvider(): AndroidJellyfinProvider {
    return this as? AndroidJellyfinProvider
        ?: error("Expected Android JellyfinProvider implementation.")
}

actual fun createJellyfinProvider(): JellyfinProvider {
    AppLogger.debug("Provider", "createJellyfinProvider() called.")
    return AndroidJellyfinProvider().also {
        AppLogger.info("Provider", "Jellyfin provider created.")
    }
}
