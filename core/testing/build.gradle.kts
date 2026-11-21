plugins {
    alias(libs.plugins.kaviyy.android.library)
}

dependencies {
    api(libs.junit)
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    implementation(libs.androidx.datastore)
    api(projects.core.network)
    implementation(libs.kotlinx.serialization.json)
}
