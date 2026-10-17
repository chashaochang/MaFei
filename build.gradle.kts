import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    base
    id("mafei.dependency-boundaries")
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

val verifyNoSvgResources = tasks.register("verifyNoSvgResources") {
    group = "verification"
    description = "Rejects SVG source resources; active app artwork must use PNG."
    val sourceRootPaths = rootProject.subprojects
        .map { project -> project.projectDir.resolve("src").absolutePath }
    inputs.property("projectRootPath", rootProject.projectDir.absolutePath)
    inputs.property("sourceRootPaths", sourceRootPaths)
    inputs.files(sourceRootPaths.map(::File))

    doLast {
        val projectRoot = File(inputs.properties.getValue("projectRootPath").toString())
        val sourceRoots = (inputs.properties.getValue("sourceRootPaths") as List<*>)
            .map { path -> File(path.toString()) }
        val offenders = sourceRoots
            .asSequence()
            .filter(File::exists)
            .flatMap { sourceRoot ->
                sourceRoot.walkTopDown()
                    .filter { file -> file.isFile && file.extension.equals("svg", ignoreCase = true) }
            }
            .map { file -> file.relativeTo(projectRoot).invariantSeparatorsPath }
            .sorted()
            .toList()

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("SVG resources are forbidden in active CMP modules. Convert them to PNG:")
                    offenders.forEach { path -> appendLine(" - $path") }
                }.trimEnd(),
            )
        }

        logger.lifecycle("PASS: ${sourceRoots.size} active CMP modules contain no SVG source resources")
    }
}

subprojects {
    configurations.configureEach {
        val platformConfiguration = this
        resolutionStrategy.eachDependency {
            // CPF Coil's Android variant targets JVM 18. Resolve upstream Coil
            // on Android without changing CPF Native metadata or the JVM target.
            if (requested.group == "io.coil-kt.coil3" &&
                platformConfiguration.attributes.getAttribute(KotlinPlatformType.attribute) == KotlinPlatformType.androidJvm
            ) {
                useVersion(libs.versions.coil.upstream.get())
                because("Android uses upstream Coil; CPF adaptation is required only on Native targets")
            }
        }
    }
    val groupSegments = path
        .split(":")
        .filter { it.isNotBlank() }
        .dropLast(1)
    group = (listOf("cn", "mafei", "cmp") + groupSegments).joinToString(".")

    tasks.matching { it.name.startsWith("prepareComposeResourcesTaskFor") }
        .configureEach {
            dependsOn(rootProject.tasks.named("verifyNoSvgResources"))
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
    dependsOn("verifyAppBoundaries")
    commandLine("node", rootProject.file("scripts/verify-cmp-foundation.mjs").absolutePath)
}

tasks.register<Exec>("verifyAppBoundaryTests") {
    group = "verification"
    description = "Tests the application boundary guard against architectural regressions."
    commandLine("node", "--test", rootProject.file("scripts/verify-app-boundaries.test.mjs").absolutePath)
}

tasks.register<Exec>("verifyAppBoundaries") {
    group = "verification"
    description = "Keeps application roots, route bindings and browser controllers within their responsibilities."
    dependsOn("verifyAppBoundaryTests")
    commandLine("node", rootProject.file("scripts/verify-app-boundaries.mjs").absolutePath)
}

tasks.register<Exec>("verifyNoNativeMaterialUi") {
    group = "verification"
    description = "Rejects native Material visual widgets in production CMP UI."
    commandLine("node", rootProject.file("scripts/verify-no-native-material-ui.mjs").absolutePath,
        rootProject.projectDir.absolutePath)
}

val verifyDependencyBoundaries by tasks.registering(Exec::class) {
    group = "verification"
    description = "Checks production source dependencies independently of CPF/ABI configuration."
    commandLine("node", rootProject.file("scripts/verify-dependency-boundaries.mjs").absolutePath)
}

tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":test"))
    dependsOn(verifyDependencyBoundaries)
    dependsOn("verifyArchitecture", verifyNoSvgResources, "verifyNoNativeMaterialUi")
}
