import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Each module chooses its target contract explicitly; its name has no target semantics.
abstract class MaFeiKmpConvention(private val target: TargetContract) : Plugin<Project> {
    enum class TargetContract { SHARED, UI, ANDROID, IOS, OHOS, DESKTOP }

    override fun apply(project: Project) = with(project) {
        check(plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
            "$path: apply Kotlin Multiplatform before the MaFei target convention"
        }
        check(extensions.findByName("mafeiTargetContract") == null) {
            "$path: choose exactly one MaFei target convention"
        }
        extensions.add("mafeiTargetContract", target.name.lowercase())
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val requiresAndroid = target in setOf(TargetContract.SHARED, TargetContract.UI, TargetContract.ANDROID)
        check(!requiresAndroid || plugins.hasPlugin("com.android.library")) {
            "$path: this target convention requires com.android.library"
        }
        check(target != TargetContract.UI || plugins.hasPlugin("org.jetbrains.compose") &&
            plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose")) {
            "$path: the UI convention requires Compose and the Compose compiler plugins"
        }
        extensions.configure<KotlinMultiplatformExtension> {
            when (target) {
                TargetContract.SHARED -> configureSharedTargets(libs)
                TargetContract.UI -> configureCmpTargets(libs)
                TargetContract.ANDROID -> configureAndroidTargetOnly(libs)
                TargetContract.IOS -> configureAppleTargetsOnly(libs)
                TargetContract.OHOS -> configureOhosTargetOnly(libs)
                TargetContract.DESKTOP -> configureDesktopTargetOnly(libs)
            }
        }
        if (requiresAndroid) extensions.configure<LibraryExtension> {
            namespace = "cn.mafei.cmp" + project.path.split(":").filter(String::isNotBlank)
                .joinToString("") { "." + it.replace("-", "_") }
            compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
            defaultConfig.minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_11
                targetCompatibility = JavaVersion.VERSION_11
            }
        }
    }
}

class MaFeiSharedConvention : MaFeiKmpConvention(TargetContract.SHARED)
class MaFeiUiConvention : MaFeiKmpConvention(TargetContract.UI)
class MaFeiAndroidConvention : MaFeiKmpConvention(TargetContract.ANDROID)
class MaFeiIosConvention : MaFeiKmpConvention(TargetContract.IOS)
class MaFeiOhosConvention : MaFeiKmpConvention(TargetContract.OHOS)
class MaFeiDesktopConvention : MaFeiKmpConvention(TargetContract.DESKTOP)

private fun KotlinMultiplatformExtension.configureCommonTests(libs: VersionCatalog) {
    sourceSets.commonTest.dependencies {
        implementation(libs.findLibrary("kotlin-test").get())
    }
}

private fun KotlinMultiplatformExtension.configureSharedTargets(libs: VersionCatalog) {
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
    configureCommonTests(libs)
}

private fun KotlinMultiplatformExtension.configureCmpTargets(libs: VersionCatalog) {
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
                implementation(libs.findLibrary("compose-runtime").get())
                implementation(libs.findLibrary("compose-foundation").get())
                implementation(libs.findLibrary("compose-ui").get())
                implementation(libs.findLibrary("compose-material3").get())
                implementation(
                    libs.findLibrary("compose-resources").get(),
                )
            }
        }

        getByName("androidMain").dependsOn(cpfUiMain)
        getByName("androidUnitTest").dependsOn(commonTest)

        val desktopMain by getting {
            dependsOn(commonMain)
            kotlin.srcDir("src/sharedUiMain/kotlin")
            dependencies {
                implementation(libs.findLibrary("compose-upstream-runtime").get())
                implementation(libs.findLibrary("compose-upstream-foundation").get())
                implementation(libs.findLibrary("compose-upstream-ui").get())
                implementation(libs.findLibrary("compose-upstream-material3").get())
                implementation(libs.findLibrary("compose-upstream-resources").get())
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
    configureCommonTests(libs)
}

private fun KotlinMultiplatformExtension.configureAndroidTargetOnly(libs: VersionCatalog) {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    configureCommonTests(libs)
}

private fun KotlinMultiplatformExtension.configureAppleTargetsOnly(libs: VersionCatalog) {
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
    configureCommonTests(libs)
}

private fun KotlinMultiplatformExtension.configureOhosTargetOnly(libs: VersionCatalog) {
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
    configureCommonTests(libs)
}

private fun KotlinMultiplatformExtension.configureDesktopTargetOnly(libs: VersionCatalog) {
    jvm("desktop")
    configureCommonTests(libs)
}
