plugins {
    alias(libs.plugins.android.library)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.atc3.emulator"
}

// A software pump for tests only: the app does not depend on this module, so it is not in any APK
// a user installs. It is built from the protocol description and does not use the driver's code.
dependencies {
    implementation(project(":core:interfaces"))
}
