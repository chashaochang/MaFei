plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}


dependencies {
    add("commonMainApi", project(":player:api"))
    add("commonMainApi", project(":player:core"))
    add("commonTestImplementation", project(":player:testkit"))
}
