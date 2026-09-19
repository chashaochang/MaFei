plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}


dependencies {
    add("commonMainApi", project(":common:core"))
    add("commonMainApi", project(":common:glass"))
    add("commonMainApi", project(":common:design-system"))
    add("desktopTestImplementation", "org.jetbrains.compose.ui:ui-test-junit4:1.9.2")
    add("desktopTestImplementation", libs.compose.desktop)
}

compose.resources {
    packageOfResClass = "cn.mafei.cmp.common.ui.generated.resources"
    generateResClass = always
}
