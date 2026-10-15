plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":providers:jellyfin:core"))
    add("commonMainImplementation", project(":providers:jellyfin:transport"))
    add("commonMainImplementation", project(":providers:jellyfin:wire"))
    add("commonMainImplementation", libs.kotlinx.coroutines.core)
    add("commonMainImplementation", libs.kotlinx.serialization.json)
}

dependencies {
    add("commonTestImplementation", project(":providers:jellyfin:testkit"))
}
