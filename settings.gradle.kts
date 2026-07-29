rootProject.name = "behandlingsdager"

val ktorVersion = "3.5.1"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
    }
    versionCatalogs {
        create("ktorLibs").from("io.ktor:ktor-version-catalog:${ktorVersion}")
    }
}

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
