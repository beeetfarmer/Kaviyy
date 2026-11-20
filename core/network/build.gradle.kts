plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.model)
    api(libs.okhttp)
    implementation(libs.okhttp.tls)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.okhttp.mockwebserver)
}
