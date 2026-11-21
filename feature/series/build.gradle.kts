plugins {
    alias(libs.plugins.kaviyy.android.feature)
}

dependencies {
    implementation(libs.androidx.paging.compose)
    implementation(projects.core.download)
}
