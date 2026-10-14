package cn.mafei.cmp

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import cn.mafei.cmp.common.design_system.MaFeiLayoutFamily
import cn.mafei.cmp.common.design_system.MaFeiPersistentSidebarWidthDp
import cn.mafei.cmp.common.design_system.NavigationPlacement
import cn.mafei.cmp.common.design_system.MaFeiWindowEnvironment
import cn.mafei.cmp.common.design_system.LocalMaFeiWindowFeatures
import cn.mafei.cmp.common.design_system.mafeiAdaptiveWindow
import cn.mafei.cmp.common.design_system.mafeiLayout
import cn.mafei.cmp.session.*

@Composable
internal fun rememberBrowseWindowLayout(maxWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp):
    cn.mafei.cmp.common.design_system.MaFeiLayout {
    val fontScale = LocalDensity.current.fontScale
    val windowFeatures = LocalMaFeiWindowFeatures.current
    return remember(maxWidth, maxHeight, fontScale, windowFeatures) {
        val adaptiveWindow = mafeiAdaptiveWindow(
            MaFeiWindowEnvironment(
                widthDp = maxWidth.value,
                heightDp = maxHeight.value,
                fontScale = fontScale,
                safeInsets = windowFeatures.safeInsets,
                occlusions = windowFeatures.occlusions,
                foldPosture = windowFeatures.foldPosture,
                displayRole = windowFeatures.displayRole,
            ),
        )
        mafeiLayout(adaptiveWindow).let { base ->
            // The desktop host owns a minimum window size and therefore
            // must not fall back to the tablet drawer when the user
            // resizes the window within the supported desktop range.
            // Content density still follows the actual width below.
            if (windowFeatures.desktopWindow) {
                base.copy(
                    family = MaFeiLayoutFamily.Desktop,
                    navigation = NavigationPlacement.Sidebar,
                    navigationWidthDp = MaFeiPersistentSidebarWidthDp.value,
                )
            } else {
                base
            }
        }
    }
}
