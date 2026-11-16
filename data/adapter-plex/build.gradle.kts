plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}

dependencies {
    add("commonMainApi", project(":data:adapter-contract"))
    add("commonMainImplementation", project(":providers:plex:core"))
}
