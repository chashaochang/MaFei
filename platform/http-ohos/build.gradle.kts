plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}


dependencies {
    add("commonMainApi", project(":core:http-contract"))
    add("commonMainImplementation", project(":platform:http-ktor"))
    add("ohosMainImplementation", libs.ktor.client.core)
    add("ohosMainImplementation", libs.kotlinx.coroutines.core)
    add("ohosMainImplementation", "io.ktor:ktor-http-cio:${libs.versions.ktor.get()}")
    // Use only the bundled libcurl C binding, not the broken upstream Kotlin engine.
    add("ohosMainImplementation", "io.ktor:ktor-client-curl-ohosarm64:${libs.versions.ktor.get()}:cinterop-libcurl@klib") {
        isTransitive = false
    }
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
}
