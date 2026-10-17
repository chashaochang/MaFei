plugins { `kotlin-dsl` }

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    testImplementation(libs.junit4)
}

gradlePlugin {
    plugins.register("mafeiDependencyBoundaries") {
        id = "mafei.dependency-boundaries"
        implementationClass = "MaFeiDependencyBoundaries"
    }
    listOf("shared" to "Shared", "ui" to "Ui", "android" to "Android",
        "ios" to "Ios", "ohos" to "Ohos", "desktop" to "Desktop").forEach { (id, implementation) ->
        plugins.register("mafeiKmp${implementation}") {
            this.id = "mafei.kmp.$id"
            implementationClass = "MaFei${implementation}Convention"
        }
    }
}
