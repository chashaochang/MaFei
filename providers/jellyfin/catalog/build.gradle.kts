plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}


dependencies {
    add("commonMainApi", project(":providers:jellyfin:core"))
    add("commonMainImplementation", project(":providers:jellyfin:transport"))
    add("commonMainImplementation", project(":providers:jellyfin:wire"))
}

dependencies {
    add("commonTestImplementation", project(":providers:jellyfin:testkit"))
}
