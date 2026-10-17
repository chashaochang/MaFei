plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    id("mafei.kmp.ui")
}


dependencies {
    add("commonMainApi", project(":core:domain"))
    add("commonMainApi", project(":common:ui"))
    add("commonMainApi", project(":common:media"))
    add("commonMainImplementation", project(":data:repositories"))
    add("commonMainApi", project(":player:ui"))
    add("desktopTestImplementation", libs.compose.upstream.ui.test)
    add("desktopTestImplementation", libs.compose.desktop)
}
