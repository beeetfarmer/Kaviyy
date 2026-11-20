plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
}

dependencies {
    api(projects.core.network)
    api(projects.core.datastore)
    api(projects.core.database)
    testImplementation(libs.robolectric)
}
