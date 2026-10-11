package cn.mafei.cmp.common.design_system

enum class AppearanceMode(val title: String, val storageValue: String) {
    System("跟随系统", "auto"), Light("浅色", "light"), Dark("深色", "dark");

    fun dark(systemDark: Boolean) = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }
}
