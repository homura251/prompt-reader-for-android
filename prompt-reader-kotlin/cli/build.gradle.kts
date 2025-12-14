plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "com.promptreader"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("com.promptreader.cli.MainKt")
}

kotlin {
    jvmToolchain(17)
}
