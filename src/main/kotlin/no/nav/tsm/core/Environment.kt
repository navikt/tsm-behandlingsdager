package no.nav.tsm.core

import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.di.*
import java.util.*
import kotlin.time.Duration

enum class RuntimeEnvironments(val nais: String) {
    LOCAL("local"),
    DEV("dev-gcp"),
    PROD("prod-gcp"),
}

class Runtime(val env: RuntimeEnvironments, val name: String)

class Environment(
    val runtime: Runtime,
    val kafka: KafkaConfig,
    val external: ExternalConfig,
    val behandlingsdagerIds: List<String>,
)

data class ExternalConfig(
    val tsmPdlCache: TsmPdlConfig,
)

data class TsmPdlConfig(
    val url: String,
)

class KafkaConfig(val config: Properties, val pollInterval: Duration)


fun initializeEnvironment(config: ApplicationConfig): Environment {
    val kafkaProperties =
        KafkaConfig(
            config =
                Properties().apply {
                    config.config("kafka.config").toMap().forEach { this[it.key] = it.value }
                },
            pollInterval = config.property("kafka.sykmeldingConsumer.longPoll").getAs()
        )
    return Environment(
        runtime =
            Runtime(
                env = config.inferRuntimeEnvironment(),
                name = config.property("app.name").getString(),
            ),
        kafka = kafkaProperties,
        external = ExternalConfig(
            TsmPdlConfig(
                url = config.property("external.tsmPdlCache.url").getString()
            )

        ),
        behandlingsdagerIds =
            config.property("behandlingsdager.ids").getString().split(',').filter {
                it.isNotEmpty()
            },
    )
}

fun Application.isLocal(): Boolean {
    val env: Environment by dependencies

    return env.runtime.env == RuntimeEnvironments.LOCAL
}

private fun ApplicationConfig.inferRuntimeEnvironment(): RuntimeEnvironments {
    return when (val configEnv = this.property("app.runtime").getString()) {
        "local" -> RuntimeEnvironments.LOCAL
        "prod-gcp" -> RuntimeEnvironments.PROD
        "dev-gcp" -> RuntimeEnvironments.DEV
        else -> {
            throw IllegalStateException(
                "Unexpected 'app.runtime' configuration: ${configEnv}. Should be one of 'local', 'dev-gcp' or 'prod-gcp'"
            )
        }
    }
}
