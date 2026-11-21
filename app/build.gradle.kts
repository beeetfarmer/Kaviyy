plugins {
    alias(libs.plugins.kaviyy.android.application)
    alias(libs.plugins.kaviyy.android.compose)
    alias(libs.plugins.kaviyy.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nl.kaviyy"

    buildFeatures {
        buildConfig = true
    }

    // Per-app language (Android 13+ system setting) lists the locales we ship strings for.
    androidResources {
        generateLocaleConfig = true
    }

    defaultConfig {
        applicationId = "com.nl.kaviyy"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.model)
    listOf(
        projects.feature.auth,
        projects.feature.home,
        projects.feature.library,
        projects.feature.series,
        projects.feature.search,
        projects.feature.filter,
        projects.feature.lists,
        projects.feature.profile,
        projects.feature.downloads,
        projects.feature.readerImage,
        projects.feature.readerEpub,
        projects.feature.readerPdf,
    ).forEach { implementation(it) }
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx) // MainActivity hosts the EPUB navigator fragment
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
}
