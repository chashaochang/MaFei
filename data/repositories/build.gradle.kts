plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

// JVM coroutine scheduling tests run on both Android unit tests and Mac Desktop.
// Native targets compile common tests separately; this is not Native runtime proof.
kotlin.sourceSets {
    getByName("desktopTest").kotlin.srcDir("src/jvmTest/kotlin")
    getByName("androidUnitTest").kotlin.srcDir("src/jvmTest/kotlin")
}


dependencies {
    add("commonMainApi", project(":common:core"))
    add("commonMainApi", project(":core:domain"))
    add("commonMainApi", project(":core:storage-api"))
    add("commonMainApi", project(":core:secure-credential-api"))
    add("commonMainApi", project(":data:adapter-contract"))
    add("commonMainImplementation", project(":data:adapter-jellyfin"))
    add("commonMainImplementation", project(":data:adapter-feiniu"))
    // Provider->player closure: the coordinator translates PlayerReports into
    // adapter.reportPlayback calls through the frozen player:api surface only.
    add("commonMainImplementation", project(":player:api"))
    add("commonTestImplementation", project(":player:testkit"))
    add("commonTestImplementation", project(":player:facade"))
    // Home snapshot persistence tests build a JsonSnapshotStorage over a fake store.
    add("commonTestImplementation", libs.kotlinx.serialization.json)
}
