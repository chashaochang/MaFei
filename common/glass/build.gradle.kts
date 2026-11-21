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
    add("desktopTestImplementation", libs.compose.desktop)
    add("desktopTestImplementation", libs.compose.upstream.ui.test)
}
