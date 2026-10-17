plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}


dependencies {
    add("commonMainApi", project(":core:http-contract"))
    add("commonMainApi", project(":core:serialization"))
    add("commonMainApi", project(":providers:jellyfin:wire"))
    add("commonMainApi", libs.kotlinx.coroutines.core)
}
