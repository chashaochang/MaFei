package cn.xiaobai.mafei.storage

import android.content.Context
import cn.xiaobai.mafei.logging.AppLogger

object AndroidAppContextHolder {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        AppLogger.debug("Bootstrap", "AndroidAppContextHolder initialized.")
    }

    fun requireContext(): Context {
        return checkNotNull(appContext) {
            AppLogger.error("Bootstrap", "AndroidAppContextHolder requireContext() before initialize().")
            "AndroidAppContextHolder is not initialized. Call initialize() in Application."
        }
    }
}
