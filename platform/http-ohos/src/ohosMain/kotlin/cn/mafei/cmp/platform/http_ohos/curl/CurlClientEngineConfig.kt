/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

// Modified for MaFei (2026-09-08): relocated, HTTP-only; see third-party/NOTICE.md.
package cn.mafei.cmp.platform.http_ohos.curl

import io.ktor.client.engine.*

/**
 * A configuration for the [HttpOnlyCurl] client engine.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=cn.mafei.cmp.platform.http_ohos.curl.CurlClientEngineConfig)
 */
internal class CurlClientEngineConfig : HttpClientEngineConfig() {
    /**
     * Forces proxy tunneling by setting `CURLOPT_HTTPPROXYTUNNEL`.
     */
    internal var forceProxyTunneling: Boolean = false

    /**
     * Sets path to Certificate Authority (CA) bundle using `CURLOPT_CAINFO`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=cn.mafei.cmp.platform.http_ohos.curl.CurlClientEngineConfig.caInfo)
     */
    public var caInfo: String? = null

    /**
     * Sets directory that holds Certificate Authority (CA) certificates using `CURLOPT_CAPATH`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=cn.mafei.cmp.platform.http_ohos.curl.CurlClientEngineConfig.caPath)
     */
    public var caPath: String? = null

    /**
     * Enables TLS host and certificate verification by setting the
     * `CURLOPT_SSL_VERIFYPEER` and `CURLOPT_SSL_VERIFYHOST` options.
     * The HTTP-only MaFei factory rejects false, including in tests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=cn.mafei.cmp.platform.http_ohos.curl.CurlClientEngineConfig.sslVerify)
     */
    public var sslVerify: Boolean = true
}
