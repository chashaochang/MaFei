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
    Full,
    Reduced,
    Opaque,
}

data class GlassCapability(
    val backdrop: Boolean,
    val blur: Boolean,
    val lens: Boolean,
    val highlight: Boolean,
    val runtimeShader: Boolean,
)

data class GlassSurfaceSpec(
    val role: GlassRole,
    val level: GlassLevel,
    val tintAlpha: Float = 0.72f,
    val intensity: Float = 1f,
    val cornerRadiusDp: Float = 24f,
)

/**
 * Stable MaFei-facing glass contract.
 *
 * CMPLiquidGlass types stay behind this module and must never leak into a
 * feature module. Missing capture or unsupported blur resolves to an opaque
 * surface; missing shader support disables refraction but retains blur.
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
    requested == GlassLevel.Full && capability.lens && capability.runtimeShader -> GlassLevel.Full
    else -> GlassLevel.Reduced
}
