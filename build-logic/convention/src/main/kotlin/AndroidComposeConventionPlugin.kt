import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("io.github.takahirom.roborazzi")
            extensions.getByType(CommonExtension::class.java).apply {
                buildFeatures.compose = true
                testOptions.unitTests.isIncludeAndroidResources = true
            }
            dependencies {
                val bom = platform(lib("androidx-compose-bom"))
                add("implementation", bom)
                add("implementation", lib("androidx-compose-ui"))
                add("implementation", lib("androidx-compose-foundation"))
                add("implementation", lib("androidx-compose-material3"))
                add("implementation", lib("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", lib("androidx-compose-ui-tooling"))
                // Screenshot tests (Robolectric + Roborazzi)
                add("testImplementation", bom)
                add("testImplementation", lib("robolectric"))
                add("testImplementation", lib("roborazzi"))
                add("testImplementation", lib("roborazzi-compose"))
                add("testImplementation", lib("androidx-compose-ui-test-junit4"))
                add("debugImplementation", lib("androidx-compose-ui-test-manifest"))
            }
        }
}
