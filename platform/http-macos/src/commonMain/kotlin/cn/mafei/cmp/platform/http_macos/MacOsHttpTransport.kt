package cn.mafei.cmp.platform.http_macos

import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.platform.http_ktor.CioHttpTransport

/** Desktop JVM binding for the ARM Mac app; no alternate TLS trust or Cookie persistence. */
fun createMacOsHttpTransport(defaultTimeoutMillis: Long = 30_000): HttpTransport =
    CioHttpTransport(defaultTimeoutMillis)
