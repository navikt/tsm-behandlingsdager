package no.nav.tsm

import com.typesafe.config.ConfigFactory
import io.kotest.matchers.equals.shouldEqual
import io.ktor.server.config.*
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class DependencyInjectionTest {

    @Test
    fun `Test local config`() = runTest {
        val conf = applicationConfig("application-local.conf", emptyMap())

        conf.tryGetString("app.name") shouldEqual "local-pod"
    }

    @Test
    fun `Test for dev-gcp`() = runTest {
        val conf = applicationConfig("application.conf", naisEnvironment("dev-gcp"))

        conf.tryGetString("app.name") shouldEqual "tsm-behandlingsdager-test"
    }

    @Test
    fun `Test for prod-gcp`() = runTest {
        val conf = applicationConfig("application.conf", naisEnvironment("prod-gcp"))

        conf.tryGetString("app.name") shouldEqual "tsm-behandlingsdager-test"
    }

    private fun applicationConfig(resource: String, env: Map<String, String>) =
        HoconApplicationConfig(
            ConfigFactory.parseMap(env)
                .withFallback(ConfigFactory.parseResources(resource))
                .resolve()
                .withoutPath("ktor")
        )

    private fun naisEnvironment(cluster: String) =
        mapOf(
            "NAIS_POD_NAME" to "tsm-behandlingsdager-test",
            "NAIS_CLUSTER_NAME" to cluster,
            "NAIS_TOKEN_ENDPOINT" to "http://localhost:7164/api/v1/token",
            "BEHANDLINGSDAGER_IDS" to "",
        )
}
