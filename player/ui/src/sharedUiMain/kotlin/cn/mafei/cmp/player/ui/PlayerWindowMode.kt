package cn.mafei.cmp.player.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** A dedicated player keeps its window header and offers only system fullscreen. */
val LocalStandalonePlayerWindow = staticCompositionLocalOf { false }
