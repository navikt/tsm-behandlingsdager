package no.nav.tsm.modules.behandlingsdager.oppgave

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import no.nav.tsm.modules.behandlingsdager.pdl.PdlClient
import no.nav.tsm.modules.behandlingsdager.pdl.PdlIdentgruppe
import no.nav.tsm.modules.behandlingsdager.pdl.PdlPerson
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class OppgaveService(private val pdlClient: PdlClient, private val kafkaProducer: OppgaveProducer) {
    suspend fun createOppgave(sykmeldingRecord: SykmeldingRecord.Digital) {
        val person = pdlClient.getPerson(sykmeldingRecord.sykmelding.pasient.fnr).getOrElse { throw RuntimeException("Fant ikke person for sykmelding: ${sykmeldingRecord.sykmelding.id}") }
        val oppgave = toOppgaveKafkaMessage(person, sykmeldingRecord)
        kafkaProducer.send(oppgave)
    }
}

private fun toOppgaveKafkaMessage(
    person: PdlPerson,
    sykmeldingRecord: SykmeldingRecord.Digital
): OpprettOppgaveKafkaMessage {
        return OpprettOppgaveKafkaMessage(
        messageId = sykmeldingRecord.sykmelding.id,
        aktoerId = person.identer.first { !it.historisk && it.gruppe == PdlIdentgruppe.AKTORID }.ident,
        tildeltEnhetsnr = "",
        opprettetAvEnhetsnr = "9999",
        behandlesAvApplikasjon = "FS22",
        orgnr = sykmeldingRecord.metadata.orgnummer,
        beskrivelse =
            "Manuell behandling av sykmelding grunnet følgende regler: Sykmelding inneholder behandlingsdager (felt 4.4).",
        temagruppe = "ANY",
        tema = "SYM",
        behandlingstema = "ab0351",
        oppgavetype = "BEH_EL_SYM",
        behandlingstype = "ANY",
        mappeId = 1,
        aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now()),
        fristFerdigstillelse =
            DateTimeFormatter.ISO_DATE.format(
                finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4))
            ),
        prioritet = PrioritetType.NORM,
        metadata = mapOf(),
        )
}



fun finnFristForFerdigstillingAvOppgave(ferdistilleDato: LocalDate): LocalDate {
    return setToWorkDay(ferdistilleDato)
}

fun setToWorkDay(ferdistilleDato: LocalDate): LocalDate =
    when (ferdistilleDato.dayOfWeek) {
        DayOfWeek.SATURDAY -> ferdistilleDato.plusDays(2)
        DayOfWeek.SUNDAY -> ferdistilleDato.plusDays(1)
        else -> ferdistilleDato
    }
