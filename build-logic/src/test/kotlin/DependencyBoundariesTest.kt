import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DependencyBoundariesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(extra: String): GradleRunner {
        val root = temporary.newFolder()
        root.resolve("settings.gradle").writeText("include ':feature:search', ':providers:jellyfin:facade', ':data:repositories', ':core:domain', ':common:ui'")
        root.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx768m\n")
        root.resolve("build.gradle").writeText("""
            plugins { id 'base'; id 'mafei.dependency-boundaries' }
            subprojects {
                configurations { commonMainImplementation; commonTestImplementation }
            }
            $extra
        """.trimIndent())
        return GradleRunner.create().withProjectDir(root).withPluginClasspath()
            .withArguments("check", "--configuration-cache", "--max-workers=1", "--console=plain")
    }

    @Test fun `feature cannot declare provider even without source imports`() {
        val result = fixture("""
            project(':feature:search') {
                dependencies { commonMainImplementation project(':providers:jellyfin:facade') }
            }
        """).buildAndFail()
        assertTrue(result.output, result.output.contains(":feature:search [commonMainImplementation] -> :providers:jellyfin:facade"))
    }

    @Test fun `inherited forbidden dependency cannot hide in a test named configuration`() {
        val result = fixture("""
            project(':feature:search') {
                configurations.commonMainImplementation.extendsFrom(configurations.commonTestImplementation)
                dependencies { commonTestImplementation project(':providers:jellyfin:facade') }
            }
        """).buildAndFail()
        assertTrue(result.output, result.output.contains(":feature:search [commonMainImplementation] -> :providers:jellyfin:facade"))
    }

    @Test fun `core cannot depend on shared UI`() {
        val result = fixture("""
            project(':core:domain') {
                dependencies { commonMainImplementation project(':common:ui') }
            }
        """).buildAndFail()
        assertTrue(result.output, result.output.contains(":core:domain [commonMainImplementation] -> :common:ui"))
    }

    @Test fun `feature repositories and isolated test dependencies pass with configuration cache`() {
        val runner = fixture("""
            project(':feature:search') {
                dependencies {
                    commonMainImplementation project(':data:repositories')
                    commonTestImplementation project(':providers:jellyfin:facade')
                }
            }
        """)
        val initial = runner.build()
        assertTrue(initial.output, initial.output.contains("production project dependency edges respect module boundaries"))
        val cached = runner.build()
        assertTrue(cached.output, cached.output.contains("Reusing configuration cache"))
    }
}
