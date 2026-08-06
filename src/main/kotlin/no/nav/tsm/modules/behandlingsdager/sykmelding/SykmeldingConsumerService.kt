package no.nav.tsm.modules.behandlingsdager.sykmelding

import no.nav.tsm.core.Environment
import no.nav.tsm.ktor.logger
import no.nav.tsm.modules.behandlingsdager.oppgave.OppgaveService
import no.nav.tsm.sykmelding.input.core.model.Aktivitet
import no.nav.tsm.sykmelding.input.core.model.RuleType
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord

class SykmeldingConsumerService(
    private val oppgaveService: OppgaveService,
    env: Environment,
) {
    private val log = logger()
    private val behandlingsdager = env.behandlingsdagerIds

    init {
        log.info("Behandlingsdager size is ${behandlingsdager.size}")
    }

    suspend fun handleRecord(record: SykmeldingRecord) {
        val behandlingsdager = record.sykmelding.aktivitet.filterIsInstance<Aktivitet.Behandlingsdager>()
        if (behandlingsdager.isEmpty()) {
            return
        }

        if (record.validation.status != RuleType.OK) {
            log.info("Behandlingsdager validation is ${record.validation.status}, skipping for now")
            return
        }

        log.info(
            "${record.sykmelding.type} with id: ${record.sykmelding.id} er enkeltstående behandlingsdager, oppretter oppgave"
        )

        oppgaveService.createOppgave(record)
    }
}
