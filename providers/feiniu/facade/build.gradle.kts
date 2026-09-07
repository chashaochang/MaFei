plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":providers:feiniu:core"))
    add("commonMainApi", project(":providers:feiniu:connection"))
    add("commonMainApi", project(":providers:feiniu:auth"))
    add("commonMainApi", project(":providers:feiniu:catalog"))
    add("commonMainApi", project(":providers:feiniu:playback"))
    add("commonMainApi", project(":providers:feiniu:assets"))
    add("commonMainApi", libs.kotlinx.coroutines.core)
}

dependencies {
    add("commonTestImplementation", project(":providers:feiniu:testkit"))
}
