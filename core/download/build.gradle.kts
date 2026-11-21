plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
}

dependencies {
    api(projects.core.data)
    testImplementation(libs.robolectric)
}
