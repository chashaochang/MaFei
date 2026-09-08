/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

// Modified for MaFei (2026-09-08): relocated, HTTP-only; see third-party/NOTICE.md.
package cn.mafei.cmp.platform.http_ohos.curl

import io.ktor.client.engine.*
import io.ktor.utils.io.*
import kotlinx.cinterop.*
import libcurl.*

// This function is thread unsafe!
// The man page asks to run it once per program,
// while the program "is still single threaded", explicitly stating that
// it should not called while any other thread is running.
// See the curl_global_init(3) man page for details.
@Suppress("DEPRECATION")
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val curlGlobalInitReturnCode = curlInitBridge()

@OptIn(ExperimentalForeignApi::class)
internal fun curlInitBridge(): Int = curl_global_init(CURL_GLOBAL_ALL.convert()).convert()

/**
 * A Kotlin/Native client engine that can be used on desktop platforms.
 *
 * To create the client with this engine, pass it to the `HttpClient` constructor:
 * ```kotlin
 * val client = HttpClient(Curl)
 * ```
 * To configure the engine, pass settings exposed by [CurlClientEngineConfig] to the `engine` method:
 * ```kotlin
 * val client = HttpClient(Curl) {
 *     engine {
 *         // this: CurlClientEngineConfig
 *     }
 * }
 * ```
 *
 * You can learn more about client engines from [Engines](https://ktor.io/docs/http-client-engines.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=cn.mafei.cmp.platform.http_ohos.curl.Curl)
 */
@OptIn(InternalAPI::class)
internal data object HttpOnlyCurl : HttpClientEngineFactory<CurlClientEngineConfig> {
    override fun create(block: CurlClientEngineConfig.() -> Unit): HttpClientEngine {
        if (curlGlobalInitReturnCode != 0) {
            throw RuntimeException("curl_global_init() returned non-zero verify: $curlGlobalInitReturnCode")
        }

        val config = CurlClientEngineConfig().apply(block)
        require(config.sslVerify) { "TLS certificate verification is mandatory" }
        return CurlClientEngine(config)
    }
}
