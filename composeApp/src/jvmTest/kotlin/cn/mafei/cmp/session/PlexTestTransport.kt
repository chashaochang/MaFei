package cn.mafei.cmp.session

import cn.mafei.cmp.core.http_contract.*
import cn.mafei.cmp.providers.plex.core.PlexAuthorizationApi

internal class PlexTestTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    var emptyServers = false
    var serverToken = "server-fixture"
    var serverHost = "media.test"
    var respond: suspend (HttpRequest) -> Pair<Int, String> = { request ->
        200 to when (request.url.encodedPath) {
            "/api/v2/pins" -> """{"id":17,"code":"pin-fixture","expiresIn":60}"""
            "/api/v2/pins/17" -> """{"authToken":"cloud-fixture"}"""
            "/api/v2/user" -> """{"id":"user-1","username":"Demo"}"""
            "/api/v2/resources" -> if (emptyServers) "[]" else """[
                {"provides":"server","clientIdentifier":"server-1","name":"Fixture","accessToken":"$serverToken",
                 "connections":[{"protocol":"https","local":true,"uri":"https://$serverHost/plex"}]}
            ]"""
            "/plex", "/plex/identity" -> """{"MediaContainer":{"machineIdentifier":"server-1"}}"""
            "/plex/library/sections" -> """{"MediaContainer":{"Directory":[{"key":"1","type":"movie","title":"Movies"}]}}"""
            else -> error("Unexpected Plex fixture request: ${request.url.encodedPath}")
        }
    }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val (status, body) = respond(request)
        return HttpResponse(status, HttpHeaders.EMPTY, body.encodeToByteArray(), request.url, request.url)
    }

    override fun close() = Unit

    suspend fun challenge() = PlexLoginChallenge(PlexAuthorizationApi(this).begin("device-fixture"))
    suspend fun servers() = PlexLoginServers(PlexAuthorizationApi(this).awaitAccount(challenge().challenge))
}
