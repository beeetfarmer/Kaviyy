plugins {
    alias(libs.plugins.kaviyy.android.feature)
}

// ponytail: Readium serves fonts only from assets, so the bundled OFL fonts are added as assets too (~2.7 MB
// duplicate of res/font); move the design system's fonts to assets if APK size matters.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(
            rootProject.file("core/designsystem/src/main/res/font").path,
        )
    }
}

dependencies {
    implementation(libs.readium.shared)
    implementation(libs.readium.streamer)
    implementation(libs.readium.navigator)
    implementation(libs.androidx.fragment.compose)
}
