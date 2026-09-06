package cn.mafei.cmp.providers.jellyfin.wire

import cn.mafei.cmp.core.serialization.IsoInstantEpochMsSerializer
import kotlinx.serialization.Serializable

/** GET /System/Info/Public — anonymous server identification. */
@Serializable
data class PublicSystemInfoDto(
    val localAddress: String? = null,
    val serverName: String? = null,
    val version: String? = null,
    val productName: String? = null,
    val id: String? = null,
    val startupWizardCompleted: Boolean? = null,
)

/** Entry of GET /Users/Public. */
@Serializable
data class PublicUserDto(
    val name: String? = null,
    val id: String,
    val primaryImageTag: String? = null,
    val hasPassword: Boolean? = null,
    val hasConfiguredPassword: Boolean? = null,
)

/** Subset of the Jellyfin user DTO returned inside authentication results. */
@Serializable
data class UserDto(
    val name: String? = null,
    val id: String,
    val serverId: String? = null,
    val primaryImageTag: String? = null,
    val hasPassword: Boolean? = null,
    val hasConfiguredPassword: Boolean? = null,
)

/** POST /Users/AuthenticateByName response. */
@Serializable
data class AuthenticationResultDto(
    val user: UserDto? = null,
    val accessToken: String? = null,
    val serverId: String? = null,
    val sessionId: String? = null,
)

/** Subset of GET /System/Info (authenticated) used for session validation. */
@Serializable
data class SystemInfoDto(
    val localAddress: String? = null,
    val serverName: String? = null,
    val version: String? = null,
    val productName: String? = null,
    val id: String? = null,
    val operatingSystem: String? = null,
)

/** Jellyfin `ProblemDetails` body used by several error responses. */
@Serializable
data class ProblemDetailsDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)

/** Generic Jellyfin query result envelope (`Items`/`TotalRecordCount`/`StartIndex`). */
@Serializable
data class QueryResultDto<T>(
    val items: List<T> = emptyList(),
    val totalRecordCount: Int = 0,
    val startIndex: Int = 0,
)
