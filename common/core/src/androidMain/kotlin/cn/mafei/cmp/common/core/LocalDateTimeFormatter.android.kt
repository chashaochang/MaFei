package cn.mafei.cmp.common.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal actual fun formatLocalDateTimePlatform(epochMilliseconds: Long, includeTime: Boolean): String =
    runCatching {
        DateTimeFormatter.ofPattern(if (includeTime) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd")
            .format(Instant.ofEpochMilli(epochMilliseconds).atZone(ZoneId.systemDefault()))
    }.getOrDefault("")
