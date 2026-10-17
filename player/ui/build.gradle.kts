plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    id("mafei.kmp.ui")
}


dependencies {
    add("commonMainApi", project(":player:api"))
    add("commonMainApi", project(":player:core"))
    add("commonMainApi", project(":common:ui"))
    add("commonMainApi", project(":common:glass"))
    add("cpfUiMainImplementation", libs.navigationevent.compose)
    add("desktopTestImplementation", libs.compose.upstream.ui.test)
    add("desktopTestImplementation", libs.compose.desktop)
}
