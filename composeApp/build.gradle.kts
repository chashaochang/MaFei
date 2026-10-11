import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Keep the old locally configured DandanPlay credentials out of tracked source files.
val generateDanmakuConfiguration by tasks.registering {
    val configuration = rootProject.layout.projectDirectory.file("legacy/harmony-arkts-reference/entry/src/main/ets/config/api-config.ets")
    val output = layout.buildDirectory.dir("generated/danmaku/kotlin")
    if (configuration.asFile.exists()) inputs.file(configuration)
    outputs.dir(output)
    doLast {
        val text = if (configuration.asFile.exists()) configuration.asFile.readText() else ""
        fun value(name: String) = Regex(name + ":\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1).orEmpty()
            .takeUnless { it.startsWith("YOUR_") }.orEmpty()
        fun literal(value: String) = groovy.json.JsonOutput.toJson(value).replace("$", "\\$")
        val target = output.get().file("cn/mafei/cmp/session/DanmakuBuildConfiguration.kt").asFile
        target.parentFile.mkdirs()
        target.writeText("package cn.mafei.cmp.session\ninternal object DanmakuBuildConfiguration {\n" +
            " val appId = ${literal(value("appId"))}\n val appSecret = ${literal(value("appSecret"))}\n" +
            " val baseUrl = ${literal(value("baseUrl").ifBlank { "https://api.dandanplay.net/api/v2" })}\n}\n")
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm("desktop")

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "MaFeiShared"
            isStatic = true
            export(project(":player:runtime-ios"))
            export(project(":player:api"))
        }
    }

    ohosArm64 {
        binaries.sharedLib {
            baseName = "kn"
            export(libs.compose.export)
            linkerOpts(
                "-lz",
                "-lnative_drawing",
                "--no-as-needed",
                "-lrawfile.z",
                "-lasset_ndk.z",
                "--as-needed",
                "-limage_source",
                "-lpixelmap",
                "-lpixelmap_ndk.z",
                "-lnative_window",
                "-lace_napi.z",
                "-lhilog_ndk.z",
                "-lhitrace_ndk.z",
                "-lffrt.z",
                "-luv",
                "-lunwind",
                "-licu",
            )
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir(generateDanmakuConfiguration)
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.koin.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(project(":data:repositories"))
                implementation(project(":data:migration"))
                implementation(project(":data:adapter-jellyfin"))
                implementation(project(":data:adapter-feiniu"))
                implementation(project(":providers:jellyfin:discovery"))
                implementation(project(":providers:jellyfin:facade"))
                implementation(project(":providers:feiniu:facade"))

                implementation(project(":common:core"))
                implementation(project(":common:glass"))
                implementation(project(":common:design-system"))
                implementation(project(":common:ui"))
                implementation(project(":common:media"))
                implementation(project(":player:facade"))
                implementation(project(":player:ui"))

                implementation(project(":feature:connect"))
                implementation(project(":feature:home"))
                implementation(project(":feature:media"))
                implementation(project(":feature:detail"))
                implementation(project(":feature:search"))
                implementation(project(":feature:favorite"))
                implementation(project(":feature:chasing"))
                implementation(project(":feature:music"))
                implementation(project(":feature:photo"))
                implementation(project(":feature:livetv"))
                implementation(project(":feature:player"))
                implementation(project(":feature:management"))
                implementation(project(":feature:settings"))
            }
        }
        val cpfUiMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/sharedUiMain/kotlin")
            dependencies {
                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.ui)
                implementation(libs.compose.material3)
                implementation(libs.compose.resources)
                implementation(libs.navigation3.runtime)
                implementation(libs.navigation3.ui)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            kotlin.srcDir("src/sharedJvmMain/kotlin")
            dependsOn(cpfUiMain)
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation("androidx.webkit:webkit:1.14.0")
                implementation(project(":player:runtime-android"))
                implementation(project(":platform:http-android"))
                implementation(project(":platform:secure-storage-android"))
            }
        }
        val androidUnitTest by getting {
            dependsOn(commonTest)
            kotlin.srcDir("src/jvmTest/kotlin")
        }

        val desktopMain by getting {
            kotlin.srcDir("src/sharedJvmMain/kotlin")
            dependsOn(commonMain)
            kotlin.srcDir("src/sharedUiMain/kotlin")
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.9.2")
                implementation("org.jetbrains.compose.foundation:foundation:1.9.2")
                implementation("org.jetbrains.compose.ui:ui:1.9.2")
                implementation("org.jetbrains.compose.material3:material3:1.9.0")
                implementation("org.jetbrains.compose.components:components-resources:1.9.2")
                implementation(
                    "org.jetbrains.androidx.navigation3:navigation3-runtime-desktop:1.0.0+dev2861",
                )
                implementation(
                    "org.jetbrains.androidx.navigation3:navigation3-ui-desktop:1.0.0+dev2861",
                )
                // CPF publishes the OHOS Compose artifacts with a -1.1.0 suffix,
                // but no matching Desktop bundle exists. MaFei only targets ARM Macs,
                // so use JetBrains' official macOS ARM64 Desktop artifact explicitly.
                implementation(libs.compose.desktop)
                // Compose's UI dispatcher does not install kotlinx.coroutines Dispatchers.Main.
                // Swing's JVM-only 1.10.2 provider is binary-compatible with the CPF core;
                // do not let its transitive graph replace our frozen multiplatform core.
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2") { isTransitive = false }
                implementation(project(":player:runtime-macos"))
                implementation(project(":platform:http-macos"))
                implementation(project(":platform:secure-storage-macos"))
            }
        }
        val desktopTest by getting {
            dependsOn(commonTest)
            kotlin.srcDir("src/jvmTest/kotlin")
        }

        val iosMain by creating {
            dependsOn(cpfUiMain)
            dependencies {
                api(project(":player:runtime-ios"))
                api(project(":player:api"))
                implementation(project(":platform:http-ios"))
                implementation(project(":platform:secure-storage-ios"))
            }
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        val iosArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosArm64Test by getting {
            dependsOn(iosTest)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }

        val ohosMain by creating {
            dependsOn(cpfUiMain)
            dependencies {
                api(libs.compose.export)
                implementation(project(":player:runtime-ohos"))
                implementation(project(":platform:http-ohos"))
                implementation(project(":platform:secure-storage-ohos"))
                implementation(project(":platform:secure-storage-core"))
            }
        }
        val ohosTest by creating {
            dependsOn(commonTest)
        }
        val ohosArm64Main by getting {
            dependsOn(ohosMain)
        }
        val ohosArm64Test by getting {
            dependsOn(ohosTest)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xlambdas=class")
    }
}

val stageNativeWebAssets by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("src/commonMain/resources/native"))
    into(layout.buildDirectory.dir("generated/nativeWebAssets/native"))
}

android {
    namespace = "cn.mafei.cmp.app"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/nativeWebAssets"))

    defaultConfig {
        applicationId = "cn.xiaobai.mafei"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "2.0.0-dev"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

tasks.named("preBuild") { dependsOn(stageNativeWebAssets) }

dependencies {
    debugImplementation(libs.compose.ui)
}

compose.resources {
    // Resources are consumed from sharedUiMain via two UI dependency trees.
    // Explicit generation avoids auto-detection requiring commonMain Compose.
    publicResClass = true
    packageOfResClass = "mafei.composeapp.generated.resources"
    generateResClass = always
}

// Explicit opt-in for local Mac acceptance only. Default and release remain Keychain-backed.
val desktopDevelopmentStorage = providers.gradleProperty("mafei.desktopDevelopmentStorage")
    .map { it == "true" }.getOrElse(false)
val desktopWindowWidth = providers.gradleProperty("mafei.desktop.windowWidth").orNull
val desktopWindowHeight = providers.gradleProperty("mafei.desktop.windowHeight").orNull
if (desktopDevelopmentStorage) {
    gradle.taskGraph.whenReady {
        check(allTasks.none { it.project == project &&
            (it.name.contains("release", ignoreCase = true) || it.name.contains("Dmg") || it.name.contains("Pkg")) }) {
            "Development credential storage is forbidden for release/installer packaging"
        }
    }
}

compose.desktop {
    application {
        mainClass = "cn.mafei.cmp.MainKt"
        if (desktopDevelopmentStorage) jvmArgs += "-Dmafei.desktop.developmentStorage=true"
        // 只保留窗口尺寸：验收统一走"真应用 + 真实数据"，假数据入口已移除（见 Main.kt）。
        desktopWindowWidth?.toIntOrNull()?.let { value ->
            if (value in 320..2400) jvmArgs += "-Dmafei.desktop.windowWidth=$value"
        }
        desktopWindowHeight?.toIntOrNull()?.let { value ->
            if (value in 400..1600) jvmArgs += "-Dmafei.desktop.windowHeight=$value"
        }

        nativeDistributions {
            modules("jdk.httpserver", "java.net.http")
            targetFormats(TargetFormat.Dmg)
            packageName = "MaFei"
            packageVersion = "2.0.0"
            macOS {
                iconFile.set(project.file("src/desktopMain/resources/app_icon.icns"))
            }
        }
    }
}

arrayOf("debug", "release").forEach { buildType ->
    val capitalizedType = buildType.replaceFirstChar { it.uppercase() }
    val harmonyEntryDir = rootProject.layout.projectDirectory.dir("harmonyApp/entry")
    val binaryOutputDir = layout.buildDirectory.dir("bin/ohosArm64/${buildType}Shared")
    val composeResourcesDir =
        harmonyEntryDir.dir("src/main/resources/rawfile/composeResources").asFile
    val composeResourcePackage =
        "${rootProject.name}.${project.name.lowercase()}.generated.resources"
    val connectResourcePackage = "cn.mafei.cmp.feature.connect.generated.resources"
    val connectResources = project(":feature:connect").layout.buildDirectory.dir(
        "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
    )
    val commonUiResourcePackage = "cn.mafei.cmp.common.ui.generated.resources"
    val commonUiResources = project(":common:ui").layout.buildDirectory.dir(
        "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
    )
    val detailResourcePackage = "cn.mafei.cmp.feature.detail.generated.resources"
    val detailResources = project(":feature:detail").layout.buildDirectory.dir(
        "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
    )

    tasks.register<Copy>("publish${capitalizedType}BinariesToHarmonyApp") {
        group = "harmony"
        description = "Publishes the ARM64 Kotlin/Native library and Compose resources."
        dependsOn("link${capitalizedType}SharedOhosArm64")
        dependsOn(":feature:connect:prepareComposeResourcesTaskForCommonMain")
        dependsOn(":common:ui:prepareComposeResourcesTaskForCommonMain")
        dependsOn(":feature:detail:prepareComposeResourcesTaskForCommonMain")
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        doNotTrackState("HarmonyApp is an external Hvigor staging directory.")
        into(harmonyEntryDir)
        from(layout.projectDirectory.dir("src/commonMain/resources/native")) {
            into("src/main/resources/rawfile/native")
        }

        from(binaryOutputDir.map { it.file("libkn_api.h") }) {
            into("src/main/cpp/include/arm64-v8a")
        }
        from(binaryOutputDir.map { it.file("libkn.so") }) {
            into("libs/arm64-v8a")
        }

        doFirst {
            composeResourcesDir.deleteRecursively()
        }

        from(layout.buildDirectory.dir(
            "generated/compose/resourceGenerator/preparedResources/commonMain/composeResources",
        )) {
            into("src/main/resources/rawfile/composeResources/${composeResourcePackage}")
        }
        from(connectResources) {
            into("src/main/resources/rawfile/composeResources/$connectResourcePackage")
        }
        from(commonUiResources) {
            into("src/main/resources/rawfile/composeResources/$commonUiResourcePackage")
        }
        from(detailResources) {
            into("src/main/resources/rawfile/composeResources/$detailResourcePackage")
        }
    }

    tasks.register("publish${capitalizedType}BinariesToOhosApp") {
        group = "harmony"
        description = "Compatibility alias for CPF documentation and tooling."
        dependsOn("publish${capitalizedType}BinariesToHarmonyApp")
    }
}
