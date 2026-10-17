plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("mafei.kmp.desktop")
}


dependencies {
    add("commonMainApi", project(":platform:http-ktor"))
    add("commonMainApi", project(":core:http-contract"))
}
