package cn.mafei.cmp.player.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** A dedicated player window already fills its content area; only system fullscreen is optional. */
val LocalStandalonePlayerWindow = staticCompositionLocalOf { false }
