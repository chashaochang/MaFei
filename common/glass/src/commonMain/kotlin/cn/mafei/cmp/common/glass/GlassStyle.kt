package cn.mafei.cmp.common.glass

/** User-selected material used by shared navigation and floating controls. */
enum class GlassStyle(val storageValue: String, val title: String) {
    Translucent("translucent", "半透明"),
    GaussianBlur("gaussian-blur", "高斯模糊"),
    Liquid("liquid", "液态玻璃"),
}
