plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.android.compose)
}

dependencies {
    api(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
}
