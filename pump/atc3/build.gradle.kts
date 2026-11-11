import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("kotlinx-serialization")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.pump.atc3"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:libraries"))
    implementation(project(":core:objects"))
    implementation(project(":core:ui"))
    implementation(project(":core:utils"))

    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // :core:objects is already an implementation dependency, so the tests see it too.
    testImplementation(project(":shared:tests"))
    // The software pump of the virtual scenarios; tests only.
    testImplementation(project(":pump:atc3-emulator"))
    testImplementation(libs.kotlinx.coroutines.test)

    ksp(libs.com.google.dagger.compiler)
    implementation(libs.com.google.dagger.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.com.google.dagger.hilt.compiler)
    ksp(libs.com.google.dagger.android.processor)
}

tasks.withType<Test>().configureEach {
    // A test that waits for an answer that never comes must fail, not hold the build for hours. The
    // test runs on a thread of its own, so even a thread stuck where it cannot be interrupted is left
    // behind and the test fails; the threads are dumped first, to show where it was stuck.
    systemProperty("junit.jupiter.execution.timeout.default", "60 s")
    systemProperty("junit.jupiter.execution.timeout.thread.mode.default", "SEPARATE_THREAD")
    systemProperty("junit.jupiter.extensions.preinterrupt.thread.dump.enabled", "true")
    // The end to end scenarios on the virtual pump run as their own step: -Patc3VirtualPump=only.
    // They talk to the pump like on a phone, with real waits: a clock that moves (see the test copy of
    // android.os.SystemClock) and more time per test.
    val virtualPump = project.findProperty("atc3VirtualPump") == "only"
    if (virtualPump) {
        systemProperty("atc3.realClock", "true")
        systemProperty("junit.jupiter.execution.timeout.default", "180 s")
    }
    filter {
        if (virtualPump) includeTestsMatching("app.aaps.pump.atc3.virtual.*")
        else excludeTestsMatching("app.aaps.pump.atc3.virtual.*")
    }
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
