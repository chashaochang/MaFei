package cn.xiaobai.mafei

import android.app.Application
import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.storage.AndroidAppContextHolder

class MaFeiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.installDefault()
        AppLogger.info("Bootstrap", "Application onCreate: Android log sink installed.")
        AndroidAppContextHolder.initialize(this)
        AppLogger.info("Bootstrap", "Application onCreate: app context initialized.")
    }
}
