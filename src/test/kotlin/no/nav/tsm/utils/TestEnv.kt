package no.nav.tsm.utils

import kotlin.time.Duration.Companion.seconds
import no.nav.tsm.core.*
import no.nav.tsm.ktor.nais.RuntimeCluster

fun integrationEnvironment(): Environment {
    return Environment(
        runtime = Runtime(env = RuntimeCluster.DEV, name = "tsm-behandlingsdager-it"),
        sykmeldingerConsumer = SykmeldingerConsumer(pollInterval = 0.seconds),
        external = ExternalConfig(tsmPdlCache = TsmPdlConfig(url = "https://test.pdl")),
        behandlingsdagerIds = listOf("1", "2"),
    )
}
