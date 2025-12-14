pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

plugins {
	// Enables automatic provisioning of Java toolchains (e.g. JDK 17) without local installs.
	id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "prompt-reader-kotlin"

include(":core")
include(":cli")
