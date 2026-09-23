plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}


dependencies {
    add("commonMainApi", project(":player:api"))
    add("commonMainApi", project(":player:core"))
    add("commonMainApi", project(":common:ui"))
    add("commonMainApi", project(":common:glass"))
    add("cpfUiMainImplementation", "org.jetbrains.androidx.navigationevent:navigationevent-compose:1.9.2-1.0.0")
}
