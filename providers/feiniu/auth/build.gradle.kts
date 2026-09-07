plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":providers:feiniu:core"))
    add("commonMainImplementation", project(":core:http-contract"))
}

dependencies {
    add("commonTestImplementation", project(":providers:feiniu:testkit"))
}

dependencies {
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
}
