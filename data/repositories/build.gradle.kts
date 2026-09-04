plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}


dependencies {
    add("commonMainApi", project(":core:domain"))
    add("commonMainApi", project(":core:storage-api"))
    add("commonMainApi", project(":core:secure-credential-api"))
    add("commonMainApi", project(":data:adapter-contract"))
    add("commonMainImplementation", project(":data:adapter-jellyfin"))
    add("commonMainImplementation", project(":data:adapter-feiniu"))
}
