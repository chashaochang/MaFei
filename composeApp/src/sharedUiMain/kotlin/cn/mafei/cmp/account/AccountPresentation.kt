package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.session.*

internal const val PRIVACY_AGREEMENT_URL = "https://agreement-drcn.hispace.dbankcloud.cn/index.html?lang=zh&agreementId=1915508141466875008"

internal fun legacyVisibleServerName(connection: SavedConnection): String {
    if (connection.server.provider != ProviderKind.Feiniu) return connection.server.name
    val name = connection.server.name.trim()
    val address = connection.server.baseUrl.trim()
    val normalized = name.lowercase()
    return if (
        name.isEmpty() ||
            name == address ||
            normalized.startsWith("http://") ||
            normalized.startsWith("https://") ||
            ".5ddd.com" in normalized ||
            ".fnos.net" in normalized
    ) {
        "飞牛影视"
    } else {
        name
    }
}

internal fun legacyConnectionDescription(connection: SavedConnection): String {
    if (connection.server.provider != ProviderKind.Feiniu) return ""
    val route = connection.feiniuRoutes.firstOrNull()
    val baseUrl = route?.baseUrl ?: connection.server.baseUrl
    val host =
        baseUrl
            .substringAfter("://", baseUrl)
            .substringBefore('/')
            .let { authority -> if (authority.startsWith('[')) authority.substringAfter('[').substringBefore(']') else authority.substringBefore(':') }
            .lowercase()
    val addressType =
        when {
            ':' in host -> "IPv6"
            host.split('.').let { parts -> parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 } } -> "IPv4"
            else -> ""
        }
    return when (route?.route) {
        "Relay" -> "飞牛中转"
        "Local" -> if (addressType.isNotEmpty()) "内网 $addressType" else "内网连接"
        "Public" -> if (addressType.isNotEmpty()) "公网 $addressType" else "公网连接"
        else ->
            when {
                host == "localhost" || host == "::1" -> "内网连接"
                host.startsWith("10.") ||
                    host.startsWith("127.") ||
                    host.startsWith("192.168.") ||
                    host.startsWith("fc") ||
                    host.startsWith("fd") ||
                    host.startsWith("fe80:") ||
                    host.split('.').let { parts -> parts.size == 4 && parts[0] == "172" && (parts[1].toIntOrNull() ?: -1) in 16..31 } ->
                    if (addressType.isNotEmpty()) "内网 $addressType" else "内网连接"
                addressType.isNotEmpty() -> "公网 $addressType"
                else -> "公网直连"
            }
    }
}
