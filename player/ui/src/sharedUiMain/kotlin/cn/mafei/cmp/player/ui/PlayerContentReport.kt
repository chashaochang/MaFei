package cn.mafei.cmp.player.ui

import androidx.compose.runtime.compositionLocalOf

typealias PlayerContentReportSubmitter =
    suspend (PlayerContentReportRequest) -> PlayerContentReportResult?

/**
 * Platform capability switch. Reporting is deliberately absent unless a host
 * explicitly enables it; the HarmonyOS host is currently the only provider.
 */
val LocalPlayerContentReportSubmitter =
    compositionLocalOf<PlayerContentReportSubmitter?> { null }

data class PlayerContentReportRequest(
    val title: String,
    val itemId: String,
    val description: String,
    val playSessionId: String? = null,
)

/** Result shown by the player after the host submits a content report. */
data class PlayerContentReportResult(
    val submitted: Boolean,
    val message: String,
    val alreadyReported: Boolean = false,
)
