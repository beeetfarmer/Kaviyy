plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // WorkManager reads its own resources when initialized in Robolectric tests.
    testOptions.unitTests.isIncludeAndroidResources = true
    // WorkManager is a process-wide singleton: a fresh JVM per test class keeps one class's jobs out of the next.
    testOptions.unitTests.all { it.forkEvery = 1 }
}

dependencies {
    api(projects.core.data)
    api(libs.androidx.work.runtime)
    testImplementation(libs.robolectric)
}
