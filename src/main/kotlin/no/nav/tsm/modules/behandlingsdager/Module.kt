package no.nav.tsm.modules.behandlingsdager

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import no.nav.tsm.core.Environment
import no.nav.tsm.ktor.di.dynamicDependencies
import no.nav.tsm.ktor.kafka.producer.KafkaRecordProducer
import no.nav.tsm.ktor.kafka.producer.createProducer
import no.nav.tsm.ktor.kafka.sykmeldinger.SykmeldingerConsumer
import no.nav.tsm.ktor.logger
import no.nav.tsm.modules.behandlingsdager.oppgave.OppgaveService
import no.nav.tsm.modules.behandlingsdager.oppgave.OpprettOppgaveKafkaMessage
import no.nav.tsm.modules.behandlingsdager.pdl.PdlClient
import no.nav.tsm.modules.behandlingsdager.pdl.PdlCloudClient
import no.nav.tsm.modules.behandlingsdager.pdl.PdlLocalClient
import no.nav.tsm.modules.behandlingsdager.sykmelding.SykmeldingConsumerService

fun Application.configureBehandlingsdager() {
    configureDependencies()
    configureConsumer()
}

private fun Application.configureConsumer() {
    val log = logger()
    val env: Environment by dependencies
    val sykmeldingConsumerService: SykmeldingConsumerService by dependencies

    install(SykmeldingerConsumer) {
        clientId = env.runtime.name
        groupId = "tsm-behandlingsdager-consumer"
        pollDuration = env.sykmeldingerConsumer.pollInterval
        onRecord = { record ->
            sykmeldingConsumerService.handleRecord(record)
        }
        onTombstone = { meta ->
            log.info("Mottok en sykmelding tombstone for ID ${meta.key}, hopper over")
        }
    }
}

private fun Application.configureDependencies() {
    dynamicDependencies {
        cloud { provide<PdlClient>(PdlCloudClient::class) }
        local { provide<PdlClient>(PdlLocalClient::class) }
    }

    dependencies {
        provide<KafkaRecordProducer<OpprettOppgaveKafkaMessage>> {
            this@configureDependencies.createProducer(topic = "teamsykmelding.oppgave-produser-oppgave")
        }
        provide(OppgaveService::class)
        provide(SykmeldingConsumerService::class)
    }
}
