plugins {
    alias(libs.plugins.kaviyy.android.library)
}

dependencies {
    api(projects.core.network)
    api(projects.core.datastore)
}
