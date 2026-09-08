package cn.mafei.cmp.platform.http_android

import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.platform.http_ktor.CioHttpTransport

/** Android binding. INTERNET permission and LAN/TLS/device acceptance remain host responsibilities. */
fun createAndroidHttpTransport(defaultTimeoutMillis: Long = 30_000): HttpTransport =
    CioHttpTransport(defaultTimeoutMillis)
