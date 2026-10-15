package cn.mafei.cmp.providers.jellyfin.management

import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.serialization.MafeiJson
import cn.mafei.cmp.providers.jellyfin.core.*
import kotlinx.serialization.json.*

/** Exact legacy server endpoints through the existing authenticated JellyfinClient. */
class JellyfinManagementApi(private val client: JellyfinClient) {
    suspend fun currentUser(): JsonObject = objectRequest("Users/Me")
    suspend fun server(): JsonObject = objectRequest("System/Info")
    suspend fun counts(): JsonObject = objectRequest("Items/Counts")
    suspend fun users(): List<JsonObject> = arrayRequest("Users")
    suspend fun tasks(): List<JsonObject> = arrayRequest("ScheduledTasks")
    suspend fun devices(): List<JsonObject> {
        // The shipped legacy SDK declares DeviceInfoDtoQueryResult, not an array.
        val document = objectRequest("Devices")
        val items = document["Items"] ?: return emptyList()
        return (items as? JsonArray ?: throw JellyfinDecodeError("Device Items is not an array")).map {
            it as? JsonObject ?: throw JellyfinDecodeError("Invalid device entry")
        }
    }
    suspend fun renameDevice(id: String, customName: String) {
        require(id.isNotBlank()); requireAdministrator()
        val url = client.config.baseUrl.newBuilder().addPathSegments("Devices/Options").addQueryParameter("id", id).build()
        client.execute(HttpRequest.builder(HttpMethod.Post, url)
            .body(HttpBody.Text(buildJsonObject { put("CustomName", customName) }.toString(), HttpHeaders.CONTENT_TYPE_JSON)).build()) {}
    }
    suspend fun revokeDevice(id: String) {
        require(id.isNotBlank())
        if (client.config.deviceId.isBlank() || id == client.config.deviceId) {
            throw JellyfinAuthorizationError("Current device cannot be revoked")
        }
        requireAdministrator()
        val url = client.config.baseUrl.newBuilder().addPathSegment("Devices").addQueryParameter("id", id).build()
        client.execute(HttpRequest.builder(HttpMethod.Delete, url).build()) {}
    }
    suspend fun sessions(): List<JsonObject> = arrayRequest("Sessions", mapOf("activeWithinSeconds" to "960"))
    suspend fun activity(offset: Int, limit: Int, minDate: String?, hasUserId: Boolean?): JsonObject {
        require(offset >= 0 && limit in 1..100)
        return objectRequest("System/ActivityLog/Entries", buildMap {
            put("StartIndex", offset.toString()); put("Limit", limit.toString())
            minDate?.takeIf { it.isNotBlank() }?.let { put("MinDate", it) }
            hasUserId?.let { put("HasUserId", it.toString()) }
        })
    }
    suspend fun sessionCommand(id: String, command: String) {
        require(command in setOf("PlayPause", "Stop"))
        sessionAction(id, "Playing/$command", null)
    }
    suspend fun sessionMessage(id: String, text: String) {
        require(text.isNotBlank())
        sessionAction(id, "Message", buildJsonObject { put("Text", text); put("TimeoutMs", 5000) })
    }
    private suspend fun sessionAction(id: String, path: String, body: JsonObject?) {
        require(id.isNotBlank())
        requireAdministrator()
        val url = client.config.baseUrl.newBuilder().addPathSegment("Sessions").addPathSegment(id).addPathSegments(path).build()
        val request = HttpRequest.builder(HttpMethod.Post, url)
        if (body != null) request.body(HttpBody.Text(body.toString(), HttpHeaders.CONTENT_TYPE_JSON))
        client.execute(request.build()) { }
    }

    suspend fun startTask(id: String) = taskAction(id, HttpMethod.Post)
    suspend fun cancelTask(id: String) = taskAction(id, HttpMethod.Delete)

    private suspend fun taskAction(id: String, method: HttpMethod) {
        require(id.isNotBlank())
        requireAdministrator()
        val target = client.config.baseUrl.newBuilder().addPathSegments("ScheduledTasks/Running").addPathSegment(id).build()
        client.execute(HttpRequest.builder(method, target).build()) { }
    }

    private suspend fun requireAdministrator() {
        val user = currentUser()
        if (((user["Policy"] as? JsonObject)?.get("IsAdministrator") as? JsonPrimitive)?.booleanOrNull != true) {
            throw JellyfinAuthorizationError("Current user is not a server administrator")
        }
    }

    private suspend fun request(path: String, query: Map<String, String> = emptyMap()): JsonElement = client.execute(
        HttpRequest.builder(HttpMethod.Get, client.config.baseUrl.newBuilder().addPathSegments(path).apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build())
            .redirectPolicy(RedirectPolicy.FollowSameOrigin).readPolicy(BodyReadPolicy.BufferedAtMost(8 * 1024 * 1024L)).build(),
    ) { MafeiJson.Default.parseToJsonElement(it.bodyText()) }

    private suspend fun objectRequest(path: String, query: Map<String, String> = emptyMap()): JsonObject = request(path, query) as? JsonObject
        ?: throw JellyfinDecodeError("Management response is not an object")
    private suspend fun arrayRequest(path: String, query: Map<String, String> = emptyMap()): List<JsonObject> = (request(path, query) as? JsonArray
        ?: throw JellyfinDecodeError("Management response is not an array")).map {
        it as? JsonObject ?: throw JellyfinDecodeError("Management array contains a non-object")
    }
}
