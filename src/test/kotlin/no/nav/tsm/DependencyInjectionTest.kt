package no.nav.tsm

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.*
import io.ktor.server.testing.*
import no.nav.tsm.utils.WithKafka
import kotlin.test.Test

class DependencyInjectionTest : WithKafka() {

    @Test
    fun `Test local config`() =
        assertAllDependenciesResolve("application-local.conf")

    @Test
    fun `Test for dev-gcp`() =
        assertAllDependenciesResolve("application.conf", naisEnvironment("dev-gcp"))

    @Test
    fun `Test for prod-gcp`() =
        assertAllDependenciesResolve("application.conf", naisEnvironment("prod-gcp"))

    private fun assertAllDependenciesResolve(
        configFile: String,
        env: Map<String, String> = emptyMap(),
    ) = testApplication {
        environment {
            config = applicationConfig(configFile, env)
        }
        application {
            module()
        }
        startApplication()
    }

    private val kafkaOverrides =
        mapOf(
            "kafka.sykmeldingConsumer.longPoll" to "PT1S",
            "kafka.config.\"bootstrap.servers\"" to kafka.bootstrapServers,
            "kafka.config.\"security.protocol\"" to "PLAINTEXT",
            "kafka.config.\"ssl.truststore.location\"" to "/path",
            "kafka.config.\"ssl.truststore.password\"" to "truststorepw",
            "kafka.config.\"ssl.keystore.location\"" to "keystorePath",
            "kafka.config.\"ssl.keystore.password\"" to "keystorePassword",
        )

    private fun applicationConfig(resource: String, env: Map<String, String>) =
        HoconApplicationConfig(
            ConfigFactory.parseMap(kafkaOverrides + env)
                .withFallback(ConfigFactory.parseResources(resource))
                .resolve()
                .withoutPath("ktor")
        )

    private fun naisEnvironment(cluster: String) =
        mapOf(
            "NAIS_POD_NAME" to "tsm-behandlingsdager-test",
            "NAIS_CLUSTER_NAME" to cluster,
            "NAIS_TOKEN_ENDPOINT" to "http://localhost:7164/api/v1/token",
        )
}
