package cn.mafei.cmp.session

import cn.mafei.cmp.core.http_contract.BodyReadPolicy
import cn.mafei.cmp.core.http_contract.HttpBody
import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.core.http_contract.RedirectPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ContentReportRequest(
    val title: String,
    val itemId: String,
    val serverUrl: String,
    val description: String,
    val playSessionId: String? = null,
    val reporterId: String? = null,
    val reporterName: String? = null,
    val client: String = "mafei-ohos",
    val source: String = "player",
)

data class ContentReportResult(
    val submitted: Boolean,
    val message: String,
    val alreadyReported: Boolean = false,
)

/**
 * Client for the existing HarmonyOS content-report endpoint. The host supplies
 * its report token from an OHOS-only generated build configuration.
 */
internal class ContentReportService(
    private val transport: HttpTransport,
    private val reportToken: String,
) {
    init {
        require(reportToken.isNotBlank())
    }

    suspend fun submit(request: ContentReportRequest): ContentReportResult {
        val payload = buildJsonObject {
            put("title", request.title)
            put("itemId", request.itemId)
            put("serverUrl", request.serverUrl)
            put("source", request.source)
            put("description", request.description)
            put("client", request.client)
            request.playSessionId?.let { put("playSessionId", it) }
            request.reporterId?.let { put("reporterId", it) }
            request.reporterName?.let { put("reporterName", it) }
        }
        val endpoint = HttpUrl.parse(ENDPOINT)
        val httpRequest = HttpRequest.builder(HttpMethod.Post, endpoint)
            .setHeader("x-report-token", reportToken)
            .body(HttpBody.Text(payload.toString(), "application/json; charset=utf-8"))
            .timeoutMillis(8_000)
            .redirectPolicy(RedirectPolicy.DoNotFollow)
            .readPolicy(BodyReadPolicy.BufferedAtMost(MAX_RESPONSE_BYTES))
            .build()
        val response = try {
            transport.execute(httpRequest)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return ContentReportResult(false, "举报提交失败，请检查网络后重试")
        }
        val body = runCatching { Json.parseToJsonElement(response.bodyText()).jsonObject }.getOrNull()
        val code = body?.get("code")?.jsonPrimitive?.contentOrNull
        val message = body?.get("message")?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.take(MAX_MESSAGE_LENGTH)
        val explicitlyRejected = body?.get("ok")?.jsonPrimitive?.contentOrNull == "false"

        return when {
            response.statusCode == 409 || code == "DUPLICATE_REPORT" ->
                ContentReportResult(
                    submitted = false,
                    message = message ?: "该内容已举报过，请勿重复举报",
                    alreadyReported = true,
                )
            response.statusCode == 429 || code == "RATE_LIMITED" ->
                ContentReportResult(false, message ?: "提交过于频繁，请稍后再试")
            response.isSuccessful && !explicitlyRejected ->
                ContentReportResult(true, message ?: "举报已提交")
            else ->
                ContentReportResult(false, message ?: "举报提交失败（HTTP ${response.statusCode}）")
        }
    }

    private companion object {
        const val ENDPOINT = "https://mafei.hmos.site/api/report"
        const val MAX_RESPONSE_BYTES = 64 * 1024L
        const val MAX_MESSAGE_LENGTH = 240
    }
}
