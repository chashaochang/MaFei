package cn.mafei.cmp.providers.jellyfin.images

import cn.mafei.cmp.core.http_contract.HttpUrl
import cn.mafei.cmp.providers.jellyfin.core.JellyfinClient
import cn.mafei.cmp.providers.jellyfin.transport.JellyfinRequests
import cn.mafei.cmp.providers.jellyfin.wire.ImageType

/**
 * Stable image URL surface. Jellyfin images are anonymous by default; pass
 * [appendApiKey] for servers with anonymous access disabled — the token is
 * attached from the current session automatically.
 */
interface JellyfinImagesApi {

    fun itemImageUrl(
        itemId: String,
        imageType: ImageType = ImageType.Primary,
        maxWidth: Int? = null,
        maxHeight: Int? = null,
        quality: Int? = null,
        tag: String? = null,
        appendApiKey: Boolean = false,
    ): HttpUrl

    fun userImageUrl(
        userId: String,
        imageType: ImageType = ImageType.Primary,
        tag: String? = null,
        appendApiKey: Boolean = false,
    ): HttpUrl
}

class DefaultJellyfinImagesApi(
    private val client: JellyfinClient,
) : JellyfinImagesApi {

    private fun tokenIfRequested(appendApiKey: Boolean): String? =
        if (appendApiKey) client.sessionState.current.value?.accessToken else null

    override fun itemImageUrl(
        itemId: String,
        imageType: ImageType,
        maxWidth: Int?,
        maxHeight: Int?,
        quality: Int?,
        tag: String?,
        appendApiKey: Boolean,
    ): HttpUrl =
        JellyfinRequests.imageUrl(
            base = client.config.baseUrl,
            itemId = itemId,
            imageType = imageType,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            quality = quality,
            tag = tag,
            token = tokenIfRequested(appendApiKey),
        )

    override fun userImageUrl(
        userId: String,
        imageType: ImageType,
        tag: String?,
        appendApiKey: Boolean,
    ): HttpUrl =
        JellyfinRequests.userImageUrl(
            base = client.config.baseUrl,
            userId = userId,
            imageType = imageType,
            tag = tag,
            token = tokenIfRequested(appendApiKey),
        )
}
