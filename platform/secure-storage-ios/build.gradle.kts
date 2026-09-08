import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin.targets.withType<KotlinNativeTarget>().configureEach {
    if (name == "iosSimulatorArm64") {
        binaries.withType<TestExecutable>().configureEach {
            // Simulator Security.framework reads test entitlements from the Mach-O
            // section, not a production signing identity. Never apply to frameworks.
            linkerOpts("-Wl,-sectcreate,__TEXT,__entitlements,${project.file("src/iosTest/resources/KeychainTest.entitlements").absolutePath}")
            linkTaskProvider.configure {
                inputs.file(project.file("src/iosTest/resources/KeychainTest.entitlements"))
            }
        }
    }
}

dependencies {
    add("commonMainImplementation", project(":platform:secure-storage-core"))
    add("commonMainImplementation", libs.kotlinx.coroutines.core)
    add("commonTestImplementation", libs.kotlinx.coroutines.core)
    add("commonMainApi", project(":core:secure-credential-api"))
}
