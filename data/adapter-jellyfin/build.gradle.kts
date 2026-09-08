plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":data:adapter-contract"))
    add("commonMainApi", project(":core:domain"))
    add("commonMainImplementation", project(":providers:jellyfin:facade"))
    // JellyfinItemQuery sits behind the catalog module's implementation
    // dependency; the adapter constructs it directly.
    add("commonMainImplementation", project(":providers:jellyfin:transport"))
}

dependencies {
    add("commonTestImplementation", project(":providers:jellyfin:testkit"))
}
