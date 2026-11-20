plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
}

dependencies {
    api(projects.core.network)
    api(projects.core.datastore)
    api(projects.core.database)
    api(libs.androidx.paging.common)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.robolectric)
}
