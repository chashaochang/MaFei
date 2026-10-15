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

/**
 * 玻璃面的材质。
 *
 * - [Liquid]：液态玻璃 —— 高斯模糊 + 边缘折射（lens）+ 一道 1dp 高光描边 + 投影。适合
 *   浮在页面上的圆角卡片 / 胶囊。
 * - [Frosted]：**只有高斯模糊 + 染色**。贴到窗口边缘的整块面板（侧边栏 / 抽屉）用它：
 *   折射在面板边缘会画出一圈可见的边（四条边各一条线，读成"浮起来的卡片"），而且整屏
 *   高度的折射没有任何视觉收益。分界线由调用方自己画一条 1dp 竖线。
 */
enum class GlassMaterial { Liquid, Frosted }

data class GlassSurfaceSpec(
    val role: GlassRole,
    val level: GlassLevel,
    val tintAlpha: Float = 0.72f,
    val intensity: Float = 1f,
    val cornerRadiusDp: Float = 24f,
    val material: GlassMaterial = GlassMaterial.Liquid,
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
