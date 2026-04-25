package cn.xiaobai.mafei.data.jellyfin

private class IosJellyfinAuthRepository : JellyfinAuthRepository {
    override suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthResult {
        return JellyfinAuthResult.Failure(
            type = JellyfinAuthFailureType.UNKNOWN,
            message = "Jellyfin login is not implemented on iOS yet.",
        )
    }
}

actual fun createJellyfinAuthRepository(provider: JellyfinProvider): JellyfinAuthRepository {
    return IosJellyfinAuthRepository()
}
