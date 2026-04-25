package cn.xiaobai.mafei.data

import java.net.URLEncoder

internal fun buildPrimaryImageUrl(
    baseUrl: String,
    itemId: String,
    accessToken: String,
    maxWidth: Int? = null,
    maxHeight: Int? = null,
): String {
    return buildArtworkUrl(
        baseUrl = baseUrl,
        accessToken = accessToken,
        path = "/Items/$itemId/Images/Primary",
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )
}

internal fun buildBackdropImageUrl(
    baseUrl: String,
    itemId: String,
    accessToken: String,
    maxWidth: Int? = null,
    maxHeight: Int? = null,
): String {
    return buildArtworkUrl(
        baseUrl = baseUrl,
        accessToken = accessToken,
        path = "/Items/$itemId/Images/Backdrop/0",
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )
}

internal fun buildThumbImageUrl(
    baseUrl: String,
    itemId: String,
    accessToken: String,
    maxWidth: Int? = null,
    maxHeight: Int? = null,
): String {
    return buildArtworkUrl(
        baseUrl = baseUrl,
        accessToken = accessToken,
        path = "/Items/$itemId/Images/Thumb",
        maxWidth = maxWidth,
        maxHeight = maxHeight,
    )
}

private fun buildArtworkUrl(
    baseUrl: String,
    accessToken: String,
    path: String,
    maxWidth: Int?,
    maxHeight: Int?,
): String {
    val cleanBase = baseUrl.trim().trimEnd('/')
    val query = mutableListOf(
        "quality=90",
        "api_key=${URLEncoder.encode(accessToken, "UTF-8")}",
    )
    if (maxWidth != null && maxWidth > 0) {
        query += "maxWidth=$maxWidth"
    }
    if (maxHeight != null && maxHeight > 0) {
        query += "maxHeight=$maxHeight"
    }
    return "$cleanBase$path?${query.joinToString("&")}"
}
