package cn.mafei.cmp.providers.feiniu.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Entry of GET /v/api/v1/mediadb/list (media library). */
@Serializable
data class FeiniuMediaLibraryEntry(
    @SerialName("guid") val guid: String,
    @SerialName("title") val title: String,
    @SerialName("posters") val posters: List<String> = emptyList(),
    @SerialName("category") val category: String? = null,
    @SerialName("view_type") val viewType: Int? = null,
)
