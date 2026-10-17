plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}

dependencies {
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
    add("desktopTestImplementation", libs.kotlinx.coroutines.core)
}
