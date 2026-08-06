package no.nav.tsm.plugins

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.tsm.core.Environment
import no.nav.tsm.core.initializeEnvironment
import no.nav.tsm.ktor.auth.texas.Texas

fun Application.configureDependencies() {
    val config = environment.config

    dependencies {
        provide<HttpClient> { configureBaseHttpClient() }
        provide<HttpClient>("RetryHttpClient") {
            createExternalApiHttpClient(resolve<HttpClient>())
        }
        provide(Texas::class)
        provide<Environment> { initializeEnvironment(config) }
    }
}

private fun configureBaseHttpClient(): HttpClient = HttpClient(Apache5) {}

private fun createExternalApiHttpClient(baseHttpClient: HttpClient) = baseHttpClient.config {
    install(HttpRequestRetry) {
        retryOnServerErrors(maxRetries = 5)
        exponentialDelay()
    }
}
