package cn.xiaobai.mafei.data.jellyfin

import cn.xiaobai.mafei.logging.AppLogger
import cn.xiaobai.mafei.logging.summarizeBaseUrlForLog
import cn.xiaobai.mafei.logging.summarizeUsernameForLog
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi

private class AndroidJellyfinAuthRepository(
    private val provider: JellyfinProvider,
) : JellyfinAuthRepository {
    override suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthResult {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val normalizedUsername = username.trim()
        AppLogger.info(
            "Auth",
            "Login start: baseUrl=${summarizeBaseUrlForLog(normalizedBaseUrl)}, username=${summarizeUsernameForLog(normalizedUsername)}."
        )
        if (normalizedBaseUrl.isBlank()) {
            AppLogger.warn("Auth", "Login validation failed: empty baseUrl.")
            return JellyfinAuthResult.Failure(
                type = JellyfinAuthFailureType.VALIDATION,
                message = "Server address is required.",
                errorCode = JellyfinErrorCode.INVALID_URL,
                retryable = false,
            )
        }
        if (normalizedUsername.isBlank() || password.isBlank()) {
            AppLogger.warn(
                "Auth",
                "Login validation failed: usernameOrPasswordEmpty username=${summarizeUsernameForLog(normalizedUsername)}."
            )
            return JellyfinAuthResult.Failure(
                type = JellyfinAuthFailureType.VALIDATION,
                message = "Username and password are required.",
                errorCode = JellyfinErrorCode.UNKNOWN,
                retryable = false,
            )
        }

        return try {
            AppLogger.debug("Auth", "Creating Jellyfin API for login.")
            val api = provider.requireAndroidProvider().jellyfin.createApi(baseUrl = normalizedBaseUrl)
            val auth = api.userApi
                .authenticateUserByName(normalizedUsername, password)
                .content
            val accessToken = auth.accessToken?.takeIf { it.isNotBlank() }
                ?: return JellyfinAuthResult.Failure(
                    type = JellyfinAuthFailureType.UNKNOWN,
                    message = "Login succeeded but no access token was returned.",
                    errorCode = JellyfinErrorCode.SERVER_ERROR,
                ).also {
                    AppLogger.warn("Auth", "Login returned without access token.")
                }
            api.update(accessToken = accessToken)
            AppLogger.info(
                "Auth",
                "Login success: baseUrl=${summarizeBaseUrlForLog(normalizedBaseUrl)}, username=${summarizeUsernameForLog(auth.user?.name ?: normalizedUsername)}."
            )
            JellyfinAuthResult.Success(
                baseUrl = normalizedBaseUrl,
                userId = auth.user?.id?.toString(),
                username = auth.user?.name?.takeIf { it.isNotBlank() } ?: normalizedUsername,
                accessToken = accessToken,
            )
        } catch (error: InvalidStatusException) {
            AppLogger.warn(
                "Auth",
                "Login failed with HTTP ${error.status}: baseUrl=${summarizeBaseUrlForLog(normalizedBaseUrl)}, username=${summarizeUsernameForLog(normalizedUsername)}."
            )
            if (error.status == 401) {
                JellyfinAuthResult.Failure(
                    type = JellyfinAuthFailureType.INVALID_CREDENTIALS,
                    message = "Invalid username or password.",
                    errorCode = JellyfinErrorCode.AUTH_FAILED,
                )
            } else if (error.status == 403) {
                JellyfinAuthResult.Failure(
                    type = JellyfinAuthFailureType.INVALID_CREDENTIALS,
                    message = "Account is authenticated but has no access permission.",
                    errorCode = JellyfinErrorCode.FORBIDDEN,
                    retryable = false,
                )
            } else if (error.status in 500..599) {
                JellyfinAuthResult.Failure(
                    type = JellyfinAuthFailureType.SERVER_UNREACHABLE,
                    message = "Server error (HTTP ${error.status}). Please retry later.",
                    errorCode = JellyfinErrorCode.SERVER_ERROR,
                )
            } else {
                JellyfinAuthResult.Failure(
                    type = JellyfinAuthFailureType.UNKNOWN,
                    message = "Login failed with HTTP ${error.status}.",
                    errorCode = JellyfinErrorCode.UNKNOWN,
                )
            }
        } catch (_: SecureConnectionException) {
            AppLogger.warn(
                "Auth",
                "Login failed with certificate warning: baseUrl=${summarizeBaseUrlForLog(normalizedBaseUrl)}."
            )
            JellyfinAuthResult.Failure(
                type = JellyfinAuthFailureType.CERTIFICATE_WARNING,
                message = "Secure connection failed. Certificate may be invalid.",
                errorCode = JellyfinErrorCode.TLS_CERTIFICATE_ERROR,
                retryable = false,
            )
        } catch (_: TimeoutException) {
            AppLogger.warn(
                "Auth",
                "Login timeout/unreachable: baseUrl=${summarizeBaseUrlForLog(normalizedBaseUrl)}."
            )
            JellyfinAuthResult.Failure(
                type = JellyfinAuthFailureType.SERVER_UNREACHABLE,
                message = "Server unreachable. Check address and network.",
                errorCode = JellyfinErrorCode.NETWORK_UNREACHABLE,
            )
        } catch (error: ApiClientException) {
            AppLogger.warn(
                "Auth",
                "Login api client exception: ${error.message ?: "<no-message>"}"
            )
            JellyfinAuthResult.Failure(
                type = JellyfinAuthFailureType.SERVER_UNREACHABLE,
                message = error.message ?: "Failed to connect to server.",
                errorCode = JellyfinErrorCode.NETWORK_UNREACHABLE,
            )
        } catch (error: Throwable) {
            AppLogger.error("Auth", "Login unexpected error.", error)
            JellyfinAuthResult.Failure(
                type = JellyfinAuthFailureType.UNKNOWN,
                message = "Unexpected login error. Please retry.",
                errorCode = JellyfinErrorCode.UNKNOWN,
            )
        }
    }
}

actual fun createJellyfinAuthRepository(provider: JellyfinProvider): JellyfinAuthRepository {
    AppLogger.debug("Auth", "createJellyfinAuthRepository() called.")
    return AndroidJellyfinAuthRepository(provider).also {
        AppLogger.info("Auth", "Jellyfin auth repository created.")
    }
}
