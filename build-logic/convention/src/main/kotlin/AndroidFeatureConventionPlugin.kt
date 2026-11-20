import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Features depend on `core:*` only, never on each other (AGENTS.md). */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("kaviyy.android.library")
            pluginManager.apply("kaviyy.android.compose")
            pluginManager.apply("kaviyy.hilt")
            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:data"))
            }
        }
}
