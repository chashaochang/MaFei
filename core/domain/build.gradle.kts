plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

dependencies {
    add("commonMainApi", libs.kotlinx.serialization.core)
    add("commonTestImplementation", libs.kotlinx.serialization.json)
}
