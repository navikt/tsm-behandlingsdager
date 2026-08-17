rootProject.name = "behandlingsdager"

val ktorVersion = "3.5.2"
val tsmKtorVersion = "1.1.3"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("io.github.ben-manes.versions.settings") version "0.61.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
    }
    versionCatalogs {
        create("ktorLibs").from("io.ktor:ktor-version-catalog:${ktorVersion}")
        create("tsmKtorLibs").from("no.nav.tsm:ktor-version-catalog:${tsmKtorVersion}")
    }
}
