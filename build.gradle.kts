import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.jetbrains.compose) apply false
}

group = "cn.mafei"
version = "2.0.0-SNAPSHOT"

allprojects {
    version = rootProject.version
}

fun KotlinMultiplatformExtension.configureCommonTests() {
    sourceSets.commonTest.dependencies {
        implementation("org.jetbrains.kotlin:kotlin-test:2.2.21-1.0.0")
    }
}

fun KotlinMultiplatformExtension.configureSharedTargets() {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    ohosArm64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting

        getByName("androidMain").dependsOn(commonMain)
        getByName("androidUnitTest").dependsOn(commonTest)
        getByName("desktopMain").dependsOn(commonMain)
        getByName("desktopTest").dependsOn(commonTest)

        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosArm64Test").dependsOn(iosTest)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Test").dependsOn(iosTest)

        val ohosMain by creating {
            dependsOn(commonMain)
        }
        val ohosTest by creating {
            dependsOn(commonTest)
        }
        getByName("ohosArm64Main").dependsOn(ohosMain)
        getByName("ohosArm64Test").dependsOn(ohosTest)
    }
    configureCommonTests()
}

fun KotlinMultiplatformExtension.configureCmpTargets() {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    jvm("desktop")
    iosArm64()
    iosSimulatorArm64()
    ohosArm64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting

        val cpfUiMain by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/sharedUiMain/kotlin")
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.9.2-1.0.0")
                implementation("org.jetbrains.compose.foundation:foundation:1.9.2-1.0.0")
                implementation("org.jetbrains.compose.ui:ui:1.9.2-1.0.0")
                implementation("org.jetbrains.compose.material3:material3:1.9.2-1.0.0")
                implementation(
                    "org.jetbrains.compose.components:components-resources:1.9.2-1.0.0",
                )
            }
        }

        getByName("androidMain").dependsOn(cpfUiMain)
        getByName("androidUnitTest").dependsOn(commonTest)

        val desktopMain by getting {
            dependsOn(commonMain)
            kotlin.srcDir("src/sharedUiMain/kotlin")
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.9.2")
                implementation("org.jetbrains.compose.foundation:foundation:1.9.2")
                implementation("org.jetbrains.compose.ui:ui:1.9.2")
                implementation("org.jetbrains.compose.material3:material3:1.9.0")
                implementation("org.jetbrains.compose.components:components-resources:1.9.2")
            }
        }
        getByName("desktopTest").dependsOn(commonTest)

        val iosMain by creating {
            dependsOn(cpfUiMain)
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosArm64Test").dependsOn(iosTest)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Test").dependsOn(iosTest)

        val ohosMain by creating {
            dependsOn(cpfUiMain)
        }
        val ohosTest by creating {
            dependsOn(commonTest)
        }
        getByName("ohosArm64Main").dependsOn(ohosMain)
        getByName("ohosArm64Test").dependsOn(ohosTest)
    }
    configureCommonTests()
}

fun KotlinMultiplatformExtension.configureAndroidTargetOnly() {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    configureCommonTests()
}

fun KotlinMultiplatformExtension.configureAppleTargetsOnly() {
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val iosMain by creating {
            dependsOn(commonMain)
        }
        val iosTest by creating {
            dependsOn(commonTest)
        }
        getByName("iosArm64Main").dependsOn(iosMain)
        getByName("iosArm64Test").dependsOn(iosTest)
        getByName("iosSimulatorArm64Main").dependsOn(iosMain)
        getByName("iosSimulatorArm64Test").dependsOn(iosTest)
    }
    configureCommonTests()
}

fun KotlinMultiplatformExtension.configureOhosTargetOnly() {
    ohosArm64()

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val ohosMain by creating {
            dependsOn(commonMain)
        }
        val ohosTest by creating {
            dependsOn(commonTest)
        }
        getByName("ohosArm64Main").dependsOn(ohosMain)
        getByName("ohosArm64Test").dependsOn(ohosTest)
    }
    configureCommonTests()
}

fun KotlinMultiplatformExtension.configureDesktopTargetOnly() {
    jvm("desktop")
    configureCommonTests()
}

subprojects {
    val groupSegments = path
        .split(":")
        .filter { it.isNotBlank() }
        .dropLast(1)
    group = (listOf("cn", "mafei", "cmp") + groupSegments).joinToString(".")

    if (path == ":composeApp") {
        return@subprojects
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        when {
            path.endsWith("-ios") -> extensions.configure<KotlinMultiplatformExtension> {
                configureAppleTargetsOnly()
            }
            path.endsWith("-ohos") -> extensions.configure<KotlinMultiplatformExtension> {
                configureOhosTargetOnly()
            }
            path.endsWith("-macos") -> extensions.configure<KotlinMultiplatformExtension> {
                configureDesktopTargetOnly()
            }
            else -> plugins.withId("com.android.library") {
                if (path.endsWith("-android")) {
                    extensions.configure<KotlinMultiplatformExtension> {
                        configureAndroidTargetOnly()
                    }
                } else if (
                    path.startsWith(":common:") && path != ":common:core" ||
                    path == ":player:ui" ||
                    path.startsWith(":feature:")
                ) {
                    plugins.withId("org.jetbrains.compose") {
                        extensions.configure<KotlinMultiplatformExtension> {
                            configureCmpTargets()
                        }
                    }
                } else {
                    extensions.configure<KotlinMultiplatformExtension> {
                        configureSharedTargets()
                    }
                }
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension> {
            namespace = "cn.mafei.cmp" + project.path
                .split(":")
                .filter { it.isNotBlank() }
                .joinToString(separator = "") { "." + it.replace("-", "_") }
            compileSdk = 37

            defaultConfig {
                minSdk = 24
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
    }

}

val missingRequiredModules = listOf(
    "core/domain",
    "core/http-contract",
    "providers/jellyfin/facade",
    "providers/feiniu/facade",
    "data/repositories",
    "common/glass",
    "common/design-system",
    "player/api",
    "player/core",
    "composeApp",
).filterNot { layout.projectDirectory.dir(it).asFile.isDirectory }

check(missingRequiredModules.isEmpty()) {
    "Missing CMP module directories: " + missingRequiredModules.joinToString()
}

tasks.register<Exec>("verifyArchitecture") {
    group = "verification"
    description = "Checks module build files, frozen CPF versions and ARM-only host configuration."
    commandLine("node", rootProject.file("scripts/verify-cmp-foundation.mjs").absolutePath)
}

tasks.named("check") {
    dependsOn("verifyArchitecture")
}
