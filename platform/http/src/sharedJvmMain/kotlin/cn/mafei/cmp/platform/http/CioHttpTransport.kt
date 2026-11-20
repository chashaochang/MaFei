package cn.mafei.cmp.platform.http

import cn.mafei.cmp.core.http_contract.HttpTransport
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint

fun createPlatformHttpTransport(defaultTimeoutMillis: Long = 30_000): HttpTransport =
    createKtorHttpTransport(CIO, defaultTimeoutMillis) {
        requestTimeout = 0
        endpoint { connectAttempts = 1 }
    }
