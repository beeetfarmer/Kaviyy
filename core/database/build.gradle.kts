plugins {
    alias(libs.plugins.kaviyy.android.library)
    alias(libs.plugins.kaviyy.hilt)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.robolectric)
}
