import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal object Sdk {
    const val COMPILE = 37
    const val COMPILE_MINOR = 1
    const val MIN = 26
    const val TARGET = 36
}

internal val JAVA_VERSION = JavaVersion.VERSION_17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun Project.lib(alias: String) = libs.findLibrary(alias).get()

/** `:feature:reader-image` -> `com.nl.kaviyy.feature.readerimage` */
internal fun Project.derivedNamespace() =
    "com.nl.kaviyy" + path.replace(':', '.').replace("-", "")

internal fun Project.configureAndroid(android: CommonExtension) {
    android.apply {
        compileSdk {
            version = release(Sdk.COMPILE) { minorApiLevel = Sdk.COMPILE_MINOR }
        }
        defaultConfig.minSdk = Sdk.MIN
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
    }
}
