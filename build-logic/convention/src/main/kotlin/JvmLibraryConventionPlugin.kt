import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            configureUnitTests(android = false)
            // The check suite runs `testDebugUnitTest`; make it cover JVM modules too.
            tasks.register("testDebugUnitTest") { dependsOn("test") }
            extensions.configure<JavaPluginExtension> {
                // Kotlin follows the Java toolchain, so both target the same JVM.
                toolchain.languageVersion.set(JavaLanguageVersion.of(JAVA_VERSION.majorVersion))
            }
        }
}
