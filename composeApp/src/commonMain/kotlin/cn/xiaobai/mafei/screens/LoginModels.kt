package cn.xiaobai.mafei.screens

import kotlinx.serialization.Serializable

@Serializable
enum class StartupRestoreStatus {
    LOADING,
    RESTORE_SUCCESS,
    SESSION_INVALID,
    NO_DEFAULT_SERVER,
}

@Serializable
enum class LoginUiStatus {
    DEFAULT,
    INPUTTING,
    VALIDATION_FAILED,
    LOGGING_IN,
    LOGIN_FAILED,
    SERVER_UNREACHABLE,
    CERTIFICATE_WARNING,
}

@Serializable
enum class ServerHealthStatus {
    UNKNOWN,
    HEALTHY,
    UNREACHABLE,
}

@Serializable
enum class ServerManagerViewStatus {
    EMPTY_LIST,
    LIST,
    ADDING,
    EDITING,
    PROBING,
    PROBE_FAILED,
    DELETE_CONFIRM,
}

@Serializable
data class JellyfinServer(
    val id: String,
    val serverName: String,
    val baseUrl: String,
    val isDefault: Boolean,
    val health: ServerHealthStatus = ServerHealthStatus.UNKNOWN,
    val errorMessage: String? = null,
)
