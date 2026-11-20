// A quick check of the parts of the native Sibionics source that have no Android in them (the sensor session and
// the reading of the box code), on a plain JVM, with the JugglucoNG core. Not part of the AAPS build: the same
// tests also run in :plugins:sibionics. This one takes about two minutes in CI instead of a full AAPS build.
plugins {
    kotlin("jvm") version "2.1.21"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

val module = "../src"
val pure = listOf("SibionicsSession.kt", "SibionicsIdentity.kt")

sourceSets {
    main {
        kotlin.srcDir("../../../sibionics-core/src/main/kotlin")
        kotlin.srcDir("$module/main/kotlin")
        kotlin.exclude { element -> element.file.path.contains("/app/aaps/plugins/sibionics/") && element.isDirectory.not() && element.name !in pure }
    }
    test {
        kotlin.srcDir("$module/test/kotlin")
    }
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
    }
}
