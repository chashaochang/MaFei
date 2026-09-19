package cn.mafei.cmp.feature.home

import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.heroSubtitle

/**
 * Home hero metadata follows the user-facing HarmonyOS baseline.
 *
 * Ratings belong to detail surfaces rather than the home hero subtitle.
 * Runtime and generic media-type labels must not be invented by the UI.
 */
internal fun homeHeroSubtitle(item: MediaItem): String? = item.heroSubtitle
