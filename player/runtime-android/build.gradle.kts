plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("mafei.kmp.android")
}


dependencies {
    add("commonMainApi", project(":player:api"))
    add("commonMainImplementation", project(":player:core"))
}
