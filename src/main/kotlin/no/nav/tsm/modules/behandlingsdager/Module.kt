package no.nav.tsm.modules.behandlingsdager

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.plugins.di.provide
import kotlinx.coroutines.launch
import no.nav.tsm.core.provideDynamic
import no.nav.tsm.modules.behandlingsdager.oppgave.OppgaveProducer
import no.nav.tsm.modules.behandlingsdager.oppgave.OppgaveService
import no.nav.tsm.modules.behandlingsdager.pdl.PdlClient
import no.nav.tsm.modules.behandlingsdager.pdl.PdlCloudClient
import no.nav.tsm.modules.behandlingsdager.pdl.PdlLocalClient
import no.nav.tsm.modules.behandlingsdager.sykmelding.SykmeldingConsumer
import no.nav.tsm.modules.behandlingsdager.sykmelding.SykmeldingConsumerService

fun Application.configureBehandlingsdager() {
    configureDependencies()
    configureConsumer()
}

private fun Application.configureConsumer() {
    val sykmeldingConsumerService: SykmeldingConsumerService by dependencies

    monitor.subscribe(ApplicationStarted) {
        launch { sykmeldingConsumerService.start() }
    }
}

private fun Application.configureDependencies() {

    dependencies {
        provideDynamic<PdlClient>(
            local = PdlLocalClient::class,
            cloud = PdlCloudClient::class,
        )
        provide(OppgaveProducer::class)
        provide(OppgaveService::class)
        provide(SykmeldingConsumer::class)
        provide(SykmeldingConsumerService::class)
    }
}
