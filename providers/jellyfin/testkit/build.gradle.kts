plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":core:http-contract"))
    add("commonMainApi", project(":providers:jellyfin:core"))
    add("commonMainApi", project(":providers:jellyfin:wire"))
}
