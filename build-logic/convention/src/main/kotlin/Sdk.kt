import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

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
internal fun Project.derivedNamespace() = "com.nl.kaviyy" + path.replace(':', '.').replace("-", "")

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

/** MASVS K7: security lint checks fail the build. */
internal fun Lint.securityChecksAsErrors() {
    abortOnError = true
    error += SECURITY_LINT_CHECKS
}

private val SECURITY_LINT_CHECKS =
    setOf(
        "AddJavascriptInterface",
        "AllowAllHostnameVerifier",
        "BadHostnameVerifier",
        "ExportedContentProvider",
        "ExportedReceiver",
        "ExportedService",
        "GetInstance",
        "GrantAllUris",
        "HardcodedDebugMode",
        "InsecureBaseConfiguration",
        "JavascriptInterface",
        "MutableImplicitPendingIntent",
        "PackageManagerGetSignatures",
        "SSLCertificateSocketFactoryCreateSocket",
        "SSLCertificateSocketFactoryGetInsecure",
        "SetJavaScriptEnabled",
        "SetWorldReadable",
        "SetWorldWritable",
        "TrustAllX509TrustManager",
        "UnsafeImplicitIntentLaunch",
        "UnsafeIntentLaunch",
        "UnspecifiedImmutableFlag",
        "UnsafeDynamicallyLoadedCode",
        "WorldReadableFiles",
        "WorldWriteableFiles",
    )

/**
 * Every module gets the same unit-test toolkit so each feature ships with tests:
 * JUnit4, kotlin-test, coroutines-test, Turbine, plus `core:testing` helpers on Android modules.
 */
internal fun Project.configureUnitTests(android: Boolean) {
    dependencies {
        add("testImplementation", lib("junit"))
        add("testImplementation", lib("kotlin-test"))
        add("testImplementation", lib("kotlinx-coroutines-test"))
        add("testImplementation", lib("turbine"))
        if (android && path != ":core:testing") add("testImplementation", project(":core:testing"))
    }
    tasks.withType<Test>().configureEach {
        // ponytail: skeleton modules have no tests yet; flip to true once every module has some.
        failOnNoDiscoveredTests.set(false)
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
