package cn.mafei.cmp.providers.jellyfin.core

import cn.mafei.cmp.core.http_contract.HttpException
import cn.mafei.cmp.core.http_contract.HttpInsecureRedirectException
import cn.mafei.cmp.core.http_contract.HttpRedirectLimitExceededException
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTimeoutException
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpTransportException
import cn.mafei.cmp.core.http_contract.AuthorizationRequestInterceptor
import cn.mafei.cmp.core.http_contract.withInterceptors
import cn.mafei.cmp.core.serialization.MafeiJson
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/**
 * The shared execution pipeline of the Jellyfin SDK: attaches the
 * `MediaBrowser` authorization/device header, maps transport and HTTP
 * failures onto [JellyfinError], and decodes JSON payloads with the frozen
 * tolerant MafeiJson configuration.
 *
 * Capability modules build requests via the transport module and call
 * [execute]/[executeDecoding]; they never touch the raw transport errors.
 */
class JellyfinClient(
    val config: JellyfinServerConfig,
    transport: HttpTransport,
    val sessionState: JellyfinSessionState = InMemoryJellyfinSessionState(),
) {

    val authorizationHeader: String
        get() = JellyfinAuthorizationHeader.format(
            clientName = config.clientName,
            deviceName = config.deviceName,
            deviceId = config.deviceId,
            appVersion = config.appVersion,
        )

    private val pipeline = transport.withInterceptors(listOf(JellyfinAuthorizationInterceptor(sessionState, this)))

    /**
     * Executes [request]. Non-2xx responses are mapped to [JellyfinError]
     * before [block] runs; transport failures map to the matching
     * [JellyfinConnectionError]/[JellyfinRedirectRejectedError].
     */
    suspend fun <T> execute(request: cn.mafei.cmp.core.http_contract.HttpRequest, block: suspend (HttpResponse) -> T): T {
        return try {
            val response = pipeline.execute(request)
            if (response.isSuccessful) {
                try {
                    block(response)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SerializationException) {
                    throw JellyfinDecodeError(
                        message = "Jellyfin response for ${request.url} is not valid protocol JSON: ${e.message}",
                        cause = e,
                    )
                } catch (e: IllegalArgumentException) {
                    throw JellyfinDecodeError(
                        message = "Jellyfin response for ${request.url} contains out-of-contract values: ${e.message}",
                        cause = e,
                    )
                }
            } else {
                throw mapStatusFailure(request, response)
            }
        } catch (e: JellyfinError) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpTimeoutException) {
            throw JellyfinConnectionError(JellyfinConnectionKind.Timeout, "Jellyfin request timed out: ${e.message}", e)
        } catch (e: HttpTransportException) {
            throw JellyfinConnectionError(e.kind.toJellyfinKind(), e.message, e)
        } catch (e: HttpRedirectLimitExceededException) {
            throw JellyfinRedirectRejectedError(e.message, e)
        } catch (e: HttpInsecureRedirectException) {
            throw JellyfinRedirectRejectedError(e.message, e)
        } catch (e: HttpException) {
            throw JellyfinDecodeError("Unhandled HTTP pipeline failure: ${e.message}", e)
        }
    }

    /** Convenience over [execute]: decodes the response body as [T] on success. */
    suspend fun <T> executeDecoding(
        request: cn.mafei.cmp.core.http_contract.HttpRequest,
        serializer: KSerializer<T>,
    ): T = execute(request) { response ->
        MafeiJson.Default.decodeFromString(serializer, response.bodyText())
    }

    private fun mapStatusFailure(
        request: cn.mafei.cmp.core.http_contract.HttpRequest,
        response: HttpResponse,
    ): JellyfinError {
        val problem = decodeProblemDetail(response)
        val detailSuffix = listOfNotNull(problem?.title, problem?.detail)
            .joinToString(" — ")
            .takeIf { it.isNotEmpty() }
            ?.let { " ($it)" }
            ?: ""
        val message = "Jellyfin ${response.statusCode} for ${request.url}$detailSuffix"
        return when (response.statusCode) {
            401 -> if (sessionState.current.value != null) {
                JellyfinAuthenticationExpiredError(message, problem)
            } else {
                JellyfinAuthenticationError(message, problem)
            }
            403 -> JellyfinAuthorizationError(message, problem)
            404 -> JellyfinNotFoundError(message)
            429 -> JellyfinRateLimitedError(retryAfterMs(response), message)
            in 500..599 -> JellyfinServerError(response.statusCode, message)
            else -> JellyfinBusinessError(response.statusCode, message, problem)
        }
    }

    private fun decodeProblemDetail(response: HttpResponse): JellyfinProblemDetail? {
        if (response.bodyBytes.isEmpty()) return null
        return try {
            val dto = MafeiJson.Default.decodeFromString(
                cn.mafei.cmp.providers.jellyfin.wire.ProblemDetailsDto.serializer(),
                response.bodyText(),
            )
            JellyfinProblemDetail(title = dto.title, detail = dto.detail, status = dto.status)
        } catch (_: Exception) {
            null // non-JSON error bodies (plain text) simply carry no detail
        }
    }

    private fun retryAfterMs(response: HttpResponse): Long? =
        response.headers.first("Retry-After")?.trim()?.toLongOrNull()?.let { it * 1000L }

    private fun cn.mafei.cmp.core.http_contract.TransportFailureKind.toJellyfinKind(): JellyfinConnectionKind = when (this) {
        cn.mafei.cmp.core.http_contract.TransportFailureKind.Dns -> JellyfinConnectionKind.Dns
        cn.mafei.cmp.core.http_contract.TransportFailureKind.Connect -> JellyfinConnectionKind.Connect
        cn.mafei.cmp.core.http_contract.TransportFailureKind.Tls -> JellyfinConnectionKind.Tls
        cn.mafei.cmp.core.http_contract.TransportFailureKind.SocketWrite -> JellyfinConnectionKind.Unknown
        cn.mafei.cmp.core.http_contract.TransportFailureKind.Unknown -> JellyfinConnectionKind.Unknown
    }
}

/**
 * Attaches the frozen `MediaBrowser` authorization header. The `Token` part
 * is only present while a session exists, so pre-login calls
 * (`AuthenticateByName`, `Users/Public`, `System/Info/Public`) naturally go
 * out without one.
 */
private class JellyfinAuthorizationInterceptor(
    private val sessionState: JellyfinSessionState,
    private val client: JellyfinClient,
) : AuthorizationRequestInterceptor {

    override suspend fun intercept(
        request: cn.mafei.cmp.core.http_contract.HttpRequest,
        chain: cn.mafei.cmp.core.http_contract.HttpInterceptorChain,
    ): cn.mafei.cmp.core.http_contract.HttpResponse =
        chain.proceed(
            request.newBuilder()
                .setHeader("Authorization", client.authorizationHeaderWithToken(sessionState.current.value?.accessToken))
                .build(),
        )

    private fun JellyfinClient.authorizationHeaderWithToken(token: String?): String =
        JellyfinAuthorizationHeader.format(
            clientName = config.clientName,
            deviceName = config.deviceName,
            deviceId = config.deviceId,
            appVersion = config.appVersion,
            token = token,
        )
}
