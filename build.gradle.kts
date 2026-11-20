plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

val ktlintVersion = libs.versions.ktlint.get()

spotless {
    kotlin {
        target("**/src/**/*.kt", "build-logic/**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
    source.setFrom(
        fileTree(rootDir) {
            include("**/src/**/*.kt")
            exclude("**/build/**")
        },
    )
}
