plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
}

dependencies {
    api(projects.core.model)
    api(libs.okhttp)
    implementation(libs.okhttp.tls)
    testImplementation(libs.okhttp.mockwebserver)
}
