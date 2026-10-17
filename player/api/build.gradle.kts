plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}


dependencies {
    add("commonMainApi", libs.kotlinx.coroutines.core)
    add("commonMainApi", project(":core:domain"))
}
