plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":data:adapter-contract"))
    add("commonMainApi", project(":core:domain"))
    add("commonMainImplementation", project(":providers:feiniu:facade"))
}

dependencies {
    add("commonTestImplementation", project(":providers:feiniu:testkit"))
}
