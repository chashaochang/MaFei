import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/** Checks declared production project edges without resolving external artifacts. */
class MaFeiDependencyBoundaries : Plugin<Project> {
    override fun apply(project: Project) {
        check(project == project.rootProject) { "Apply mafei.dependency-boundaries to the root only" }
        val verification = project.tasks.register<VerifyProjectDependencyBoundaries>("verifyProjectDependencyBoundaries") {
            group = "verification"
            description = "Rejects forbidden production project dependencies, including inherited configuration edges."
        }
        project.gradle.projectsEvaluated {
            val edges = project.allprojects.flatMap { source ->
                source.configurations.filterNot { it.name.contains("test", ignoreCase = true) }.flatMap { configuration ->
                    configuration.allDependencies.withType(ProjectDependency::class.java).map { dependency ->
                        "${source.path}|${configuration.name}|${dependency.path}"
                    }
                }
            }.distinct().sorted()
            // Snapshot plain values during configuration: task execution is configuration-cache safe.
            verification.configure { productionEdges.set(edges) }
        }
        project.plugins.withId("base") {
            project.tasks.named("check").configure { dependsOn(verification) }
        }
    }
}

abstract class VerifyProjectDependencyBoundaries : DefaultTask() {
    @get:Input
    abstract val productionEdges: ListProperty<String>

    @TaskAction
    fun verify() {
        val edges = productionEdges.get()
        val violations = edges.mapNotNull { edge ->
            val (source, configuration, target) = edge.split('|')
            if (forbiddenProjectDependency(source, target)) "$source [$configuration] -> $target" else null
        }
        if (violations.isNotEmpty()) throw GradleException(
            "Forbidden production project dependencies:\n" + violations.joinToString("\n") { " - $it" },
        )
        logger.lifecycle("PASS: ${edges.size} production project dependency edges respect module boundaries")
    }
}

// Matches the source-level architectural contract. Source imports and Gradle declarations
// are complementary checks: a feature can consume repositories, never provider/host APIs.
internal fun forbiddenProjectDependency(source: String, target: String): Boolean {
    fun targetIn(vararg groups: String) = groups.any { target.startsWith(":$it:") || target == ":$it" }
    val sharedUi = setOf(":common:ui", ":common:glass", ":common:design-system", ":common:media")
    return when {
        source.startsWith(":core:") -> targetIn("feature", "providers", "data", "platform", "composeApp") || target in sharedUi
        source.startsWith(":providers:") -> targetIn("feature", "data", "composeApp")
        source.startsWith(":feature:") -> targetIn("providers", "platform", "composeApp")
        source in sharedUi -> targetIn("feature", "data", "providers", "composeApp")
        source in setOf(":player:api", ":player:core") -> targetIn("feature", "data", "providers", "platform", "composeApp")
        else -> false
    }
}
