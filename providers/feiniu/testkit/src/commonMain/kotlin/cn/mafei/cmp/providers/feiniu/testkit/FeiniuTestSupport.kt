package cn.mafei.cmp.providers.feiniu.testkit

import cn.mafei.cmp.core.http_contract.HttpHeaders
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpResponse
import cn.mafei.cmp.core.http_contract.HttpTransport
import cn.mafei.cmp.core.http_contract.HttpUrl

/**
 * Scripted transport for Feiniu protocol tests: queues responses in order
 * and records every request. Set-Cookie headers can be attached per
 * response to exercise credential rotation.
 */
class FakeFeiniuTransport : HttpTransport {

    private val queue = ArrayDeque<HttpResponse>()
    private val recorded = mutableListOf<HttpRequest>()

    val requests: List<HttpRequest> get() = recorded

    fun enqueue(response: HttpResponse) {
        queue.addLast(response)
    }

    fun enqueueJson(status: Int, body: String, url: String = "https://feiniu.test", setCookies: List<String> = emptyList()) {
        val headers = HttpHeaders.Builder()
            .add(HttpHeaders.CONTENT_TYPE, "application/json")
        setCookies.forEach { headers.add(HttpHeaders.SET_COOKIE, it) }
        enqueue(
            HttpResponse(
                statusCode = status,
                headers = headers.build(),
                bodyBytes = body.encodeToByteArray(),
                requestUrl = HttpUrl.parse(url),
                effectiveUrl = HttpUrl.parse(url),
            ),
        )
    }

    fun enqueueNoContent(url: String = "https://feiniu.test") {
        enqueue(
            HttpResponse(204, HttpHeaders.EMPTY, ByteArray(0), HttpUrl.parse(url), HttpUrl.parse(url)),
        )
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        recorded += request
        return queue.removeFirstOrNull()
            ?: throw IllegalStateException("FakeFeiniuTransport has no scripted response for ${request.url}")
    }
}

/** Sanitized Feiniu fixtures (snake_case, synthetic ids only). */
data object FeiniuFixtures {
    const val BASE_URL: String = "https://feiniu.test"

    const val LOGIN_OK: String = """{"code":0,"msg":"","token":"fixture-token-0000000000000001"}"""

    const val LOGIN_BAD_CREDENTIALS: String = """{"code":1,"msg":"用户名或密码错误"}"""

    const val AUTH_EXPIRED: String = """{"code":100401,"msg":"token失效"}"""

    const val USER_INFO: String = """{"code":0,"msg":"","data":{"username":"demo","nickname":"Demo","user_id":"10001","avatar":"/v/api/v1/sys/img/a.png"}}"""

    const val MEDIA_DB_LIST: String = """{"code":0,"msg":"","data":[
        {"guid":"lib-0001","title":"电影","posters":["/p/1.jpg"],"category":"movie","view_type":1},
        {"guid":"lib-0002","title":"电视剧","posters":[],"category":"tv","view_type":2},
        {"guid":"lib-0003","title":"Future","posters":[],"category":"hologram","view_type":3,"vendorExtra":{"a":1}}
    ]}"""

    const val ITEM_LIST: String = """{"code":0,"msg":"","data":{"total":2,"list":[
        {"guid":"it-0001","title":"Fixture Movie","type":"Movie","poster":"/p/m.jpg","can_play":1,"watched":0,"is_favorite":1,"vote_average":"8.1","duration":7200,"release_date":"2024-01-01","media_stream":{"resolutions":["4K"],"progress":0.5,"current_time":3600.0}},
        {"guid":"it-0002","title":"Fixture Show","type":"TV","poster":"/p/s.jpg","season_number":1,"number_of_seasons":2}
    ]}}"""

    const val ITEM_DETAIL: String = """{"code":0,"msg":"","data":{"guid":"it-0001","title":"Fixture Movie","type":"Movie","original_title":"Fixture Movie (Original)","overview":"Overview text","poster":"/p/summary.jpg","still_path":"/p/still.jpg","status":"Released","vote_average":"8.1","release_date":"2024-01-01","air_date":"2024-01-02","imdb_id":"tt0000001","trim_id":"tr-0001","posters":"[\u0022/p/m.jpg\u0022]","poster_width":1000,"poster_height":1500,"content_ratings":"PG-13","single_child_guid":"child-1","play_item_guid":"play-1","logic_type":2}}"""

    const val SEASON_LIST: String = """{"code":0,"msg":"","data":[
        {"guid":"se-0001","title":"第一季","type":"TV","season_number":1,"parent_guid":"it-0002","local_number_of_episodes":12}
    ]}"""

    const val EPISODE_LIST: String = """{"code":0,"msg":"","data":[
        {"guid":"ep-0001","title":"第一集","type":"Episode","episode_number":1,"season_number":1,"parent_guid":"se-0001","can_play":1}
    ]}"""

    const val SEARCH_LIST: String = """{"code":0,"msg":"","data":[
        {"guid":"it-0001","title":"Fixture Movie","type":"Movie"}
    ]}"""

    const val PLAY_INFO: String = """{"code":0,"msg":"","data":{"guid":"it-0001","media_guid":"md-0001","video_guid":"vd-0001","audio_guid":"au-0001","subtitle_guid":"sb-0001","type":"Movie","item":{"title":"Fixture Movie","type":"Movie","duration":7200}}}"""

    const val PLAY_PLAY: String = """{"code":0,"msg":"","data":{"media_guid":"md-0001","video_guid":"vd-0001","audio_guid":"au-0001","subtitle_guid":"sb-0001","play_link":"/v/api/v1/stream/fixture?sign=abc","subtitle_link":"/v/api/v1/subtitle/dl/sb-0001","is_subtitle_external":1,"audio_index":1,"video_index":0,"subtitle_index":3,"hls_time":6,"not_supported_resolution":"8K","supported_highest_resolution":"4K"}}"""

    const val FN_CONNECT: String = """{"code":0,"msg":"","data":{"ipv4":["192.168.1.10"],"ipv6":[],"publicIpv4":["203.0.113.10"],"publicIpv6":["2001:db8::1"],"fn":["relay-fixture.fnos gala.invalid"],"port":{"httpPort":5666,"httpsPort":5667}}}"""
}
