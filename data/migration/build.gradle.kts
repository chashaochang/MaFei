plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":core:domain"))
    add("commonMainApi", project(":core:storage-api"))
    add("commonMainApi", project(":core:secure-credential-api"))
    // SettingsBackedMigrationSink serializes commits with a coroutine mutex.
    add("commonMainImplementation", libs.kotlinx.coroutines.core)
}
