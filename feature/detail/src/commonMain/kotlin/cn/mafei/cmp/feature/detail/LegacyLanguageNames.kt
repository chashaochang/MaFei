package cn.mafei.cmp.feature.detail

import cn.mafei.cmp.core.domain.mediaLanguageDisplayName

/**
 * Detail-page alias of the shared ISO 639 display-name table in `core:domain`
 * (same single source the provider adapters name player tracks from).
 */
internal fun legacyIsoLanguageDisplayName(code: String): String = mediaLanguageDisplayName(code)
