plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    // WorkManager reads its own resources when initialized in Robolectric tests.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(projects.core.data)
    api(libs.androidx.work.runtime)
    testImplementation(libs.robolectric)
}
