pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Only the foojay toolchain resolver lives exclusively on the plugin portal (MASVS K6).
        gradlePluginPortal {
            content { includeGroupByRegex("org\\.gradle\\.toolchains.*") }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Kaviyy"

include(":app")
listOf("model", "designsystem", "network", "database", "datastore", "data", "download", "sync", "testing")
    .forEach { include(":core:$it") }
listOf(
    "auth",
    "home",
    "library",
    "series",
    "search",
    "filter",
    "lists",
    "profile",
    "downloads",
    "reader-image",
    "reader-epub",
    "reader-pdf",
).forEach { include(":feature:$it") }
