plugins {
    kotlin("jvm") version "2.1.21"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("failed")
        showStandardStreams = false
    }
}
