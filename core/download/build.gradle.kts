plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.data)
    api(libs.androidx.work.runtime)
    testImplementation(libs.robolectric)
}
