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
    add("commonMainImplementation", project(":common:core"))
    add("cpfUiMainImplementation", libs.coil.compose)
    add("desktopMainImplementation", libs.coil.compose.upstream)
    add("desktopTestImplementation", "org.jetbrains.compose.ui:ui-test-junit4:1.9.2")
    add("desktopTestImplementation", libs.compose.desktop)
}
