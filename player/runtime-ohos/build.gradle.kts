plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}


dependencies {
    add("commonMainApi", project(":player:api"))
    add("commonMainImplementation", project(":player:core"))
}
