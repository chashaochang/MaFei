package cn.mafei.cmp.common.glass

/** User-selected material used by shared navigation and floating controls. */
enum class GlassStyle(val storageValue: String, val title: String) {
    Translucent("translucent", "半透明"),
    GaussianBlur("gaussian-blur", "高斯模糊"),
    Liquid("liquid", "液态玻璃"),
}

/** Fallback surfaces remain readable without liquid refraction/highlights. */
internal fun GlassStyle.surfaceAlpha(requested: Float): Float = when (this) {
    GlassStyle.Translucent -> requested.coerceIn(.60f, 1f)
    GlassStyle.GaussianBlur -> requested.coerceIn(.42f, 1f)
    GlassStyle.Liquid -> requested.coerceIn(0f, 1f)
}
