package cn.mafei.cmp.platform.http_ohos

import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.platform.http_ktor.createKtorHttpTransport
import cn.mafei.cmp.platform.http_ohos.curl.HttpOnlyCurl

/** HTTP-only Curl binding. TLS verification is mandatory; redirects are driven by the shared contract. */
fun createOhosHttpTransport(defaultTimeoutMillis: Long = 30_000): HttpTransport =
    createKtorHttpTransport(HttpOnlyCurl, defaultTimeoutMillis) { sslVerify = true }
