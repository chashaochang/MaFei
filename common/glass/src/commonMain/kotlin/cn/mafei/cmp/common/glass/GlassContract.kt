package cn.mafei.cmp.common.glass

enum class GlassRole {
    Content,
    Card,
    Navigation,
    Overlay,
    Dialog,
    PlayerOverlay,
}

enum class GlassLevel {
    Frosted,
    Opaque,
}

data class GlassCapability(
    val backdrop: Boolean,
    val blur: Boolean,
)

data class GlassSurfaceSpec(
    val role: GlassRole,
    val level: GlassLevel,
    val tintAlpha: Float = 0.72f,
    val cornerRadiusDp: Float = 24f,
)

/**
 * Stable MaFei-facing glass contract.
 *
 * CMPLiquidGlass types stay behind this module and must never leak into a
 * feature module. Missing capture or unsupported blur resolves to an opaque
 * surface.
 */
interface GlassCapabilityProvider {
    val capability: GlassCapability
}

fun resolveGlassLevel(
    requested: GlassLevel,
    capability: GlassCapability,
    hasBackdrop: Boolean,
): GlassLevel = when {
    requested == GlassLevel.Opaque || !hasBackdrop || !capability.backdrop || !capability.blur -> GlassLevel.Opaque
    else -> requested
}
