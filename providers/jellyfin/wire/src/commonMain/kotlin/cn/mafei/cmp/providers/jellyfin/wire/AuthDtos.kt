package cn.mafei.cmp.providers.jellyfin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /System/Info/Public — anonymous server identification.
 * Serial names follow OpenAPI (Jellyfin 10.10.7) exactly.
 */
@Serializable
data class PublicSystemInfoDto(
    @SerialName("LocalAddress") val localAddress: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("ProductName") val productName: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("StartupWizardCompleted") val startupWizardCompleted: Boolean? = null,
)

/** Entry of GET /Users/Public. */
@Serializable
data class PublicUserDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Id") val id: String,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("HasPassword") val hasPassword: Boolean? = null,
    @SerialName("HasConfiguredPassword") val hasConfiguredPassword: Boolean? = null,
)

/** Subset of the Jellyfin user DTO returned inside authentication results. */
@Serializable
data class UserDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
    @SerialName("Id") val id: String,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("HasPassword") val hasPassword: Boolean? = null,
    @SerialName("HasConfiguredPassword") val hasConfiguredPassword: Boolean? = null,
)

/** POST /Users/AuthenticateByName response. */
@Serializable
data class AuthenticationResultDto(
    @SerialName("User") val user: UserDto? = null,
    @SerialName("SessionInfo") val sessionInfo: SessionInfoDto? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)

/**
 * P0 subset of the official `SessionInfoDto` nested in an authentication
 * result. Jellyfin exposes the server session identifier as `SessionInfo.Id`;
 * there is no top-level `SessionId` field in `AuthenticationResult`.
 */
@Serializable
data class SessionInfoDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("UserId") val userId: String? = null,
    @SerialName("UserName") val userName: String? = null,
    @SerialName("Client") val client: String? = null,
    @SerialName("DeviceId") val deviceId: String? = null,
    @SerialName("DeviceName") val deviceName: String? = null,
    @SerialName("ApplicationVersion") val applicationVersion: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
)

/** Subset of GET /System/Info (authenticated) used for session validation. */
@Serializable
data class SystemInfoDto(
    @SerialName("LocalAddress") val localAddress: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("ProductName") val productName: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("OperatingSystem") val operatingSystem: String? = null,
)

/**
 * Jellyfin `ProblemDetails` body. Its fields are lowerCamel on the wire —
 * deliberately NOT PascalCase like the rest of the API (mainline-pinned).
 */
@Serializable
data class ProblemDetailsDto(
    @SerialName("type") val type: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("detail") val detail: String? = null,
    @SerialName("instance") val instance: String? = null,
)

/** Generic Jellyfin query result envelope. */
@Serializable
data class QueryResultDto<T>(
    @SerialName("Items") val items: List<T> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerialName("StartIndex") val startIndex: Int = 0,
)
