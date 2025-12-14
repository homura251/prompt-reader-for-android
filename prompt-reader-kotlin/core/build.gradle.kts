plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.promptreader"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    // org.json exists on Android; on JVM we add it for CLI/tests.
    implementation("org.json:json:20240303")
}

kotlin {
    jvmToolchain(17)
}
