plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("mafei.kmp.ios")
}


dependencies {
    add("commonMainApi", project(":core:http-contract"))
    add("commonMainImplementation", project(":platform:http-ktor"))
    add("iosMainImplementation", libs.ktor.client.darwin)
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
}
