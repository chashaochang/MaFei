plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    id("mafei.kmp.ui")
}


dependencies {
    add("commonMainApi", project(":common:core"))
    add("commonMainApi", project(":common:glass"))
    add("commonMainApi", project(":common:design-system"))
    add("cpfUiMainImplementation", libs.navigationevent.compose)
    add("desktopMainImplementation", libs.navigationevent.upstream.compose)
    add("desktopTestImplementation", libs.compose.upstream.ui.test)
    add("desktopTestImplementation", libs.compose.desktop)
}

compose.resources {
    packageOfResClass = "cn.mafei.cmp.common.ui.generated.resources"
    generateResClass = always
}
