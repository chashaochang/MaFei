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
)

/**
 * Stable MaFei-facing glass contract.
 *
 * CMPLiquidGlass types stay behind this module and must never leak into a
 * feature module. The first bootstrap implementation deliberately uses the
 * opaque fallback until the locked CMPLiquidGlass source dependency is wired.
 */
interface GlassCapabilityProvider {
    val capability: GlassCapability
}
