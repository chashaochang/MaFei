plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}


dependencies {
    add("commonMainApi", project(":core:domain"))
    add("commonMainApi", project(":common:ui"))
    add("commonMainApi", project(":common:media"))
    add("commonMainImplementation", project(":common:glass"))
    add("commonMainImplementation", project(":common:design-system"))
    add("commonMainImplementation", project(":data:repositories"))
    add("cpfUiMainImplementation", "org.jetbrains.androidx.navigationevent:navigationevent-compose:1.9.2-1.0.0")
    add("desktopMainImplementation", "androidx.navigationevent:navigationevent-compose:1.0.0-alpha06")
    add("desktopTestImplementation", "org.jetbrains.compose.ui:ui-test-junit4:1.9.2")
    add("desktopTestImplementation", libs.compose.desktop)
}

compose.resources {
    packageOfResClass = "cn.mafei.cmp.feature.detail.generated.resources"
    generateResClass = always
}
