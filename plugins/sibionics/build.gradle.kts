import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.metro)
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.sibionics"
    // The Sibionics core from JugglucoNG is compiled into this module unchanged, from its own folder
    // (sibionics-core/, GPL-3.0, see its README). Its own tests run there (sibionics-ci.yml).
    sourceSets.getByName("main").kotlin.srcDir("../../sibionics-core/src/main/kotlin")
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    // Camera reading of the code on the sensor box, as in :plugins:sync.
    implementation(libs.com.google.zxing.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    testImplementation(project(":shared:tests"))
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
