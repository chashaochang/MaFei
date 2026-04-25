package cn.xiaobai.mafei.data.jellyfin

private class IosJellyfinConnectionProbe : JellyfinConnectionProbe {
    override suspend fun probe(rawBaseUrl: String): JellyfinProbeResult {
        return JellyfinProbeResult.Failure(
            message = "Jellyfin connection probe is not implemented on iOS yet.",
            isCertificateIssue = false,
        )
    }
}

actual fun createJellyfinConnectionProbe(provider: JellyfinProvider): JellyfinConnectionProbe {
    return IosJellyfinConnectionProbe()
}
