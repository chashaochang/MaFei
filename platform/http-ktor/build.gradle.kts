plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

// Shared transport logic remains common Kotlin. Local HTTP-server tests execute
// on both JVM runners; this does not substitute for Native network acceptance.
kotlin.sourceSets {
    getByName("desktopTest").kotlin.srcDir("src/jvmTest/kotlin")
    getByName("androidUnitTest").kotlin.srcDir("src/jvmTest/kotlin")
}

dependencies {
    add("commonMainApi", project(":core:http-contract"))
    add("commonMainImplementation", libs.ktor.client.core)
    add("commonMainImplementation", libs.ktor.client.cio)
    add("commonMainImplementation", libs.kotlinx.coroutines.core)
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
}
