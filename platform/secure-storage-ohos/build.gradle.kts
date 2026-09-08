plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    add("commonMainApi", project(":core:secure-credential-api"))
    add("commonMainImplementation", project(":platform:secure-storage-core"))
    add("commonMainImplementation", libs.kotlinx.coroutines.core)
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
}
