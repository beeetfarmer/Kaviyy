plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.network)
    api(projects.core.datastore)
    api(projects.core.database)
    api(libs.androidx.paging.common)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.robolectric)
}
