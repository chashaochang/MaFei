package cn.xiaobai.mafei.data.jellyfin

import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.logging.summarizeBaseUrlForLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.net.URL
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLException

private class AndroidJellyfinConnectionProbe(
    @Suppress("UNUSED_PARAMETER")
    private val provider: JellyfinProvider,
) : JellyfinConnectionProbe {
    override suspend fun probe(rawBaseUrl: String): JellyfinProbeResult {
        val normalizedBaseUrl = normalizeBaseUrl(rawBaseUrl)
        AppLogger.info("Probe", "Probe start: baseUrl=${summarizeBaseUrlForLog(normalizedBaseUrl)}.")
        if (normalizedBaseUrl.isBlank()) {
            AppLogger.warn("Probe", "Probe validation failed: empty baseUrl.")
            return JellyfinProbeResult.Failure(
                message = "Server address is required.",
                errorCode = JellyfinErrorCode.INVALID_URL,
                retryable = false,
            )
        }

        return withContext(Dispatchers.IO) {
            val validatedBaseUrl = try {
                val uri = URI(normalizedBaseUrl)
                val scheme = uri.scheme?.lowercase()
                if (scheme != "http" && scheme != "https") {
                    AppLogger.warn("Probe", "Probe validation failed: invalid scheme.")
                    return@withContext JellyfinProbeResult.Failure(
                        message = "Server address must start with http:// or https://.",
                        errorCode = JellyfinErrorCode.INVALID_URL,
                        retryable = false,
                    )
                }
                uri.toURL()
                normalizedBaseUrl
            } catch (error: Throwable) {
                AppLogger.warn("Probe", "Probe URL parse failed.", error)
                return@withContext JellyfinProbeResult.Failure(
                    message = "Unable to parse the server address.",
                    errorCode = JellyfinErrorCode.INVALID_URL,
                    retryable = false,
                )
            }

            val probeTargets = listOf(
                "$validatedBaseUrl/System/Info/Public",
                "$validatedBaseUrl/System/Info",
            )

            var lastFailure: JellyfinProbeResult.Failure? = null
            for (target in probeTargets) {
                AppLogger.debug("Probe", "Probe endpoint attempt: ${summarizeBaseUrlForLog(target)}.")
                val result = probeSingleEndpoint(target, normalizedBaseUrl)
                when (result) {
                    is JellyfinProbeResult.Success -> {
                        AppLogger.info(
                            "Probe",
                            "Probe success: baseUrl=${summarizeBaseUrlForLog(result.normalizedBaseUrl)}."
                        )
                        return@withContext result
                    }
                    is JellyfinProbeResult.Failure -> {
                        AppLogger.warn(
                            "Probe",
                            "Probe endpoint failed: code=${result.errorCode}, retryable=${result.retryable}, certificate=${result.isCertificateIssue}."
                        )
                        lastFailure = result
                    }
                }
            }

            lastFailure ?: JellyfinProbeResult.Failure(
                message = "Probe failed. Please verify server address.",
                errorCode = JellyfinErrorCode.UNKNOWN,
            ).also {
                AppLogger.warn("Probe", "Probe finished with unknown failure.")
            }
        }
    }
}

private fun probeSingleEndpoint(
    targetUrl: String,
    normalizedBaseUrl: String,
): JellyfinProbeResult {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 5_000
            instanceFollowRedirects = true
            useCaches = false
            doInput = true
            setRequestProperty("Accept", "application/json")
        }

        connection.connect()
        val status = connection.responseCode
        AppLogger.debug("Probe", "Probe HTTP response: url=${summarizeBaseUrlForLog(targetUrl)}, status=$status.")
        when {
            status in 200..299 -> JellyfinProbeResult.Success(normalizedBaseUrl)
            status == 401 || status == 403 -> JellyfinProbeResult.Success(normalizedBaseUrl)
            status in 300..399 -> JellyfinProbeResult.Success(normalizedBaseUrl)
            status == 404 -> JellyfinProbeResult.Failure(
                message = "This address does not look like a Jellyfin server.",
                errorCode = JellyfinErrorCode.INVALID_URL,
                retryable = false,
            )
            status in 500..599 -> JellyfinProbeResult.Failure(
                message = "Server error (HTTP $status). Please retry later.",
                errorCode = JellyfinErrorCode.SERVER_ERROR,
            )
            else -> JellyfinProbeResult.Failure(
                message = "Connection test failed with HTTP $status.",
                errorCode = JellyfinErrorCode.UNKNOWN,
            )
        }
    } catch (error: MalformedURLException) {
        AppLogger.warn("Probe", "Probe malformed URL.", error)
        JellyfinProbeResult.Failure(
            message = "Unable to parse the server address.",
            errorCode = JellyfinErrorCode.INVALID_URL,
            retryable = false,
        )
    } catch (error: IllegalArgumentException) {
        AppLogger.warn("Probe", "Probe illegal argument.", error)
        JellyfinProbeResult.Failure(
            message = "Unable to parse the server address.",
            errorCode = JellyfinErrorCode.INVALID_URL,
            retryable = false,
        )
    } catch (error: SSLHandshakeException) {
        AppLogger.warn("Probe", "Probe certificate handshake failure.", error)
        JellyfinProbeResult.Failure(
            message = "Secure connection failed. Please check HTTPS certificate.",
            isCertificateIssue = true,
            errorCode = JellyfinErrorCode.TLS_CERTIFICATE_ERROR,
            retryable = false,
        )
    } catch (error: SSLException) {
        AppLogger.warn("Probe", "Probe SSL failure.", error)
        JellyfinProbeResult.Failure(
            message = "Secure connection failed. Please check HTTPS certificate.",
            isCertificateIssue = true,
            errorCode = JellyfinErrorCode.TLS_CERTIFICATE_ERROR,
            retryable = false,
        )
    } catch (error: SocketTimeoutException) {
        AppLogger.warn("Probe", "Probe timeout.", error)
        JellyfinProbeResult.Failure(
            message = "Server unreachable. Check address and network, then retry.",
            errorCode = JellyfinErrorCode.NETWORK_UNREACHABLE,
        )
    } catch (error: UnknownHostException) {
        AppLogger.warn("Probe", "Probe unknown host.", error)
        JellyfinProbeResult.Failure(
            message = "Server unreachable. Check address and network, then retry.",
            errorCode = JellyfinErrorCode.NETWORK_UNREACHABLE,
        )
    } catch (error: SecurityException) {
        AppLogger.error("Probe", "Probe blocked by security policy.", error)
        JellyfinProbeResult.Failure(
            message = "Network permission or security policy blocked the connection test.",
            errorCode = JellyfinErrorCode.NETWORK_UNREACHABLE,
            retryable = false,
        )
    } catch (error: IOException) {
        AppLogger.warn("Probe", "Probe IO failure: ${error.message ?: "<no-message>"}", error)
        JellyfinProbeResult.Failure(
            message = error.message?.takeIf { it.isNotBlank() }
                ?: "Server unreachable. Check address and network, then retry.",
            errorCode = JellyfinErrorCode.NETWORK_UNREACHABLE,
        )
    } catch (error: Throwable) {
        AppLogger.error("Probe", "Probe unexpected failure.", error)
        JellyfinProbeResult.Failure(
            message = "Probe failed. Please verify server address.",
            errorCode = JellyfinErrorCode.UNKNOWN,
        )
    } finally {
        connection?.disconnect()
    }
}

actual fun createJellyfinConnectionProbe(provider: JellyfinProvider): JellyfinConnectionProbe {
    AppLogger.debug("Probe", "createJellyfinConnectionProbe() called.")
    return AndroidJellyfinConnectionProbe(provider).also {
        AppLogger.info("Probe", "Jellyfin connection probe created.")
    }
}
