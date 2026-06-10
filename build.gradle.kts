import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.gradle.versions)
}

group = "no.nav.tsm"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.kafka.client)
    implementation(libs.ktor.server.di)
    implementation(libs.logback.classic)
    implementation(libs.khealth)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.ktor.server.metrics.micrometer)
    implementation(libs.ktor.client.apache)
    implementation(libs.otel.annotations)
    implementation(libs.tsm.sykmeldinger.input)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.test.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}


tasks {

}

tasks.withType<Detekt>().configureEach {
    config.setFrom(file("detekt.yml"))
    buildUponDefaultConfig = true

    dependsOn("spotlessApply")
}

tasks.register<Exec>("preRunLocal") {
    group = "application"
    commandLine("./scripts/pre-dev.sh")
}

tasks.register<JavaExec>("runLocal") {
    group = "application"
    mainClass.set("io.ktor.server.netty.EngineMain")
    classpath = sourceSets["main"].runtimeClasspath

    args("-config=application-local.conf")
    jvmArgs("-Dio.ktor.development=true", "-Dlogback.configurationFile=logback-local.xml")

    dependsOn("preRunLocal")
}
/**
 * Disable auto running of detekt on build and stuff
 */
afterEvaluate {
    tasks.named("check") {
        setDependsOn(dependsOn.filter { !it.toString().contains("detekt") })
    }
}
