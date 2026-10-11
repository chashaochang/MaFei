plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":providers:jellyfin:core"))
    add("commonMainImplementation", project(":providers:jellyfin:transport"))
    add("commonMainApi", project(":providers:jellyfin:wire"))
}
