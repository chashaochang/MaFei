plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    id("mafei.kmp.shared")
}

// The browser is independent of Compose. Its JVM scheduling tests run on both hosts.
kotlin.sourceSets {
    getByName("desktopTest").kotlin.srcDir("src/jvmTest/kotlin")
    getByName("androidUnitTest").kotlin.srcDir("src/jvmTest/kotlin")
}

dependencies {
    add("commonMainApi", project(":core:domain"))
    add("commonMainApi", project(":data:repositories"))
    add("commonMainApi", libs.kotlinx.coroutines.core)
    add("commonTestImplementation", libs.kotlinx.serialization.json)
}
