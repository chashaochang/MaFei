package cn.xiaobai.mafei.data.jellyfin

private class IosJellyfinProvider : JellyfinProvider

actual fun createJellyfinProvider(): JellyfinProvider = IosJellyfinProvider()
