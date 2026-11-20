plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    id("test-module-dependencies")
    // The vendored JugglucoNG tests are JUnit 4: this brings the JUnit 4 bridge.
    id("compose-test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.source.sibionics"
}

dependencies {
    implementation(project(":core:interfaces"))
}
