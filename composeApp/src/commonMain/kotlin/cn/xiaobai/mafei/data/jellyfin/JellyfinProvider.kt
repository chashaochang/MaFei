package cn.xiaobai.mafei.data.jellyfin

interface JellyfinProvider

expect fun createJellyfinProvider(): JellyfinProvider

fun normalizeBaseUrl(input: String): String = input.trim().trimEnd('/')
