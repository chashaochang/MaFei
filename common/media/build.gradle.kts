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
}
