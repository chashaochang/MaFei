plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

// Host-only runner for the injected common transaction tests. The app still
// consumes only ohosArm64; no JVM implementation of AssetStore is provided.
kotlin { jvm("host") }

dependencies {
    add("commonMainApi", project(":core:secure-credential-api"))
    add("commonMainImplementation", project(":platform:secure-storage-core"))
    add("commonMainImplementation", libs.kotlinx.coroutines.core)
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
}
