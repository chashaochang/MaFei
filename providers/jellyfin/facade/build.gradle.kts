plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":providers:jellyfin:core"))
    add("commonMainApi", project(":providers:jellyfin:discovery"))
    add("commonMainApi", project(":providers:jellyfin:auth"))
    add("commonMainApi", project(":providers:jellyfin:catalog"))
    add("commonMainApi", project(":providers:jellyfin:search"))
    add("commonMainApi", project(":providers:jellyfin:playstate"))
    add("commonMainApi", project(":providers:jellyfin:playback"))
    add("commonMainApi", project(":providers:jellyfin:images"))
    add("commonMainApi", project(":providers:jellyfin:music"))
    add("commonMainApi", project(":providers:jellyfin:photos"))
    add("commonMainApi", project(":providers:jellyfin:livetv"))
    add("commonMainApi", project(":providers:jellyfin:management"))
}
