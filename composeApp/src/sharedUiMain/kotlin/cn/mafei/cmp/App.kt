package cn.mafei.cmp

import androidx.compose.runtime.Composable

@Composable
fun App(onThemeChanged: (Boolean) -> Unit = {}) {
    FoundationProbeScreen(onThemeChanged)
}
