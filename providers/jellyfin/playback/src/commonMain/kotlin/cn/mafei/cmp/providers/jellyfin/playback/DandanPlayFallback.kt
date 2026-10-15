package cn.mafei.cmp.providers.jellyfin.playback

import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.core.serialization.MafeiJson
import kotlinx.serialization.json.*

/** Application-owned credentials, supplied by the build's existing private configuration. */
class DandanPlayConfiguration(val baseUrl: HttpUrl, val appId: String, val appSecret: String) {
    init { require(appId.isNotBlank() && appSecret.isNotBlank()) }
    override fun toString() = "DandanPlayConfiguration(credentials=<redacted>)"
}

/** Same plugin-miss fallback as old PlayerPageViewModel.searchDanmuku, not a second player. */
class DandanPlayFallback(private val transport: HttpTransport, private val configuration: DandanPlayConfiguration) {
    suspend fun xml(seriesName: String, episodeNumber: Int): String? {
        if (seriesName.isBlank() || episodeNumber < 0) return null
        val searchUrl = configuration.baseUrl.newBuilder().addPathSegments("search/episodes")
            .addQueryParameter("anime", seriesName).addQueryParameter("episode", episodeNumber.toString()).build()
        val search = get(searchUrl) ?: return null
        if ((search["success"] as? JsonPrimitive)?.booleanOrNull != true) return null
        val anime = (search["animes"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val episode = (anime["episodes"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val id = (episode["episodeId"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 } ?: return null
        val commentsUrl = configuration.baseUrl.newBuilder().addPathSegments("comment").addPathSegment(id.toString())
            .addQueryParameter("withRelated", "true").build()
        val comments = (get(commentsUrl)?.get("comments") as? JsonArray).orEmpty()
        val rows = comments.asSequence().mapNotNull { comment ->
            val data = comment as? JsonObject ?: return@mapNotNull null
            val p = (data["p"] as? JsonPrimitive)?.content?.split(',') ?: return@mapNotNull null
            val time = p.getOrNull(0)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return@mapNotNull null
            val mode = p.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..6 } ?: return@mapNotNull null
            val color = p.getOrNull(2)?.toIntOrNull()?.takeIf { it in 0..0xFFFFFF } ?: return@mapNotNull null
            val message = (data["m"] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)?.take(4096) ?: return@mapNotNull null
            "<d p=\"$time,$mode,25,$color\">${escapeXml(message)}</d>"
        }.take(40_000).toList()
        return rows.takeIf { it.isNotEmpty() }?.joinToString("", "<i>", "</i>")
    }

    private suspend fun get(url: HttpUrl): JsonObject? {
        val request = HttpRequest.builder(HttpMethod.Get, url)
            .header("X-AppId", configuration.appId).header("X-AppSecret", configuration.appSecret)
            .redirectPolicy(RedirectPolicy.FollowSameOrigin).timeoutMillis(12_000)
            .readPolicy(BodyReadPolicy.BufferedAtMost(8 * 1024 * 1024L)).build()
        val response = transport.execute(request)
        if (!response.isSuccessful) return null
        return MafeiJson.Default.parseToJsonElement(response.bodyText()) as? JsonObject
    }
    private fun escapeXml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
