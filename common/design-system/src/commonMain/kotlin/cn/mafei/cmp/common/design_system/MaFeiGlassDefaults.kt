package cn.mafei.cmp.common.design_system

import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurfaceSpec

/** Product material tokens. Effects never change component geometry. */
object MaFeiGlassDefaults {
    fun spec(
        role: GlassRole,
        level: GlassLevel = GlassLevel.Full,
        reduceTransparency: Boolean = false,
    ): GlassSurfaceSpec = GlassSurfaceSpec(
        role = role,
        level = if (reduceTransparency) GlassLevel.Opaque else level,
        tintAlpha = when (role) {
            GlassRole.Dialog -> 0.88f
            GlassRole.Navigation -> 0.64f
            GlassRole.Overlay, GlassRole.PlayerOverlay -> 0.56f
            else -> 0.64f
        },
        cornerRadiusDp = when (role) {
            GlassRole.Navigation, GlassRole.Dialog -> 28f
            else -> 24f
        },
    )
}
