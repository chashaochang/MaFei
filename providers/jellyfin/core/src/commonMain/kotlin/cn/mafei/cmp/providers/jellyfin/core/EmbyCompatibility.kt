package cn.mafei.cmp.providers.jellyfin.core

import cn.mafei.cmp.core.http_contract.HttpMethod
import cn.mafei.cmp.core.http_contract.HttpRequest
import cn.mafei.cmp.core.http_contract.HttpUrl

/** Emby 4.x uses user-scoped catalog routes instead of Jellyfin 10.x routes. */
internal fun embyCatalogRequest(base: HttpUrl, request: HttpRequest): HttpRequest {
    if (request.method != HttpMethod.Get ||
        request.url.scheme != base.scheme || request.url.host != base.host || request.url.port != base.port ||
        request.url.pathSegments.take(base.pathSegments.size) != base.pathSegments
    ) return request
    val user = request.url.queryParameters.first("userId") ?: return request
    val path = request.url.pathSegments.drop(base.pathSegments.size)
    val suffix = when {
        path == listOf("UserViews") -> listOf("Views")
        path == listOf("Items") -> listOf("Items")
        path == listOf("UserItems", "Resume") -> listOf("Items", "Resume")
        path == listOf("Items", "Latest") -> listOf("Items", "Latest")
        path.size == 2 && path[0] == "Items" -> listOf("Items", path[1])
        else -> return request
    }
    val url = base.newBuilder().addPathSegment("Users").addPathSegment(user)
        .apply { suffix.forEach(::addPathSegment) }
        .queryParameters {
            request.url.queryParameters.forEach { (key, value) -> if (key != "userId") add(key, value) }
        }.build()
    return request.newBuilder().url(url).build()
}
