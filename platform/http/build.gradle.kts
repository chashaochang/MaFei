plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}

kotlin.sourceSets {
    for (name in listOf("androidMain", "desktopMain")) {
        named(name) {
            kotlin.srcDir("src/sharedJvmMain/kotlin")
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
    }
    getByName("desktopTest").kotlin.srcDir("src/jvmTest/kotlin")
    getByName("androidUnitTest").kotlin.srcDir("src/jvmTest/kotlin")
}

dependencies {
    add("commonMainApi", project(":core:http-contract"))
    add("commonMainImplementation", libs.ktor.client.core)
    add("commonMainImplementation", libs.kotlinx.coroutines.core)
    add("iosMainImplementation", libs.ktor.client.darwin)
    add("ohosMainImplementation", "io.ktor:ktor-http-cio:${libs.versions.ktor.get()}")
    // The OHOS adapter uses the bundled libcurl C binding, not the upstream Kotlin engine.
    add("ohosMainImplementation", "io.ktor:ktor-client-curl-ohosarm64:${libs.versions.ktor.get()}:cinterop-libcurl@klib") {
        isTransitive = false
    }
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
}
