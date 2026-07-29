package no.nav.tsm.core

import com.typesafe.config.ConfigFactory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.server.config.HoconApplicationConfig
import kotlin.test.Test

class EnvironmentTest {

    private fun config(overrides: Map<String, String>) =
        HoconApplicationConfig(
            ConfigFactory.parseMap(baseNaisVars + overrides)
                .withFallback(ConfigFactory.parseResources("application.conf"))
                .resolve()
        )

    private val baseNaisVars =
        mapOf(
            "NAIS_POD_NAME" to "tsm-behandlingsdager-123",
            "NAIS_CLUSTER_NAME" to "prod-gcp",
            "SOURCE_VERSION_URL" to "v1",
            "NAIS_TOKEN_ENDPOINT" to "https://texas/token",
            "KAFKA_BROKERS" to "kafka-1:9092,kafka-2:9092",
            "KAFKA_TRUSTSTORE_PATH" to "/secrets/truststore.jks",
            "KAFKA_CREDSTORE_PASSWORD" to "credstore-pwd",
            "KAFKA_KEYSTORE_PATH" to "/secrets/keystore.p12",
            "BEHANDLINGSDAGER_IDS" to ","
        )

    @Test
    fun `resolves a complete production environment`() {
        val environment = initializeEnvironment(config(emptyMap()))

        environment.runtime.env shouldBe RuntimeEnvironments.PROD
        environment.runtime.name shouldBe "tsm-behandlingsdager-123"
        environment.external.tsmPdlCache.url shouldBe "http://tsm-pdl-cache"
        environment.kafka.config["bootstrap.servers"] shouldBe "kafka-1:9092,kafka-2:9092"
    }

    @Test
    fun `infers dev-gcp runtime`() {
        val environment = initializeEnvironment(config(mapOf("NAIS_CLUSTER_NAME" to "dev-gcp")))
        environment.runtime.env shouldBe RuntimeEnvironments.DEV
    }

    @Test
    fun `infers local runtime`() {
        val environment = initializeEnvironment(config(mapOf("NAIS_CLUSTER_NAME" to "local")))
        environment.runtime.env shouldBe RuntimeEnvironments.LOCAL
    }

    @Test
    fun `throws on an unexpected runtime`() {
        shouldThrow<IllegalStateException> {
            initializeEnvironment(config(mapOf("NAIS_CLUSTER_NAME" to "staging")))
        }
    }
}
