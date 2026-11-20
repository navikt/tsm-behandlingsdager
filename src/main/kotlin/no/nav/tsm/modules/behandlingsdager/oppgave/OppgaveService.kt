package no.nav.tsm.modules.behandlingsdager.oppgave

import arrow.core.getOrElse
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.tsm.core.Environment
import no.nav.tsm.ktor.kafka.producer.KafkaRecordProducer
import no.nav.tsm.ktor.logger
import no.nav.tsm.ktor.nais.RuntimeCluster
import no.nav.tsm.modules.behandlingsdager.oppgave.tsmOppgave.OpprettOppgaveRequest
import no.nav.tsm.modules.behandlingsdager.oppgave.tsmOppgave.TsmOppgaveClient
import no.nav.tsm.modules.behandlingsdager.pdl.PdlClient
import no.nav.tsm.modules.behandlingsdager.pdl.PdlIdentgruppe
import no.nav.tsm.modules.behandlingsdager.pdl.PdlPerson
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.sykmelding.input.core.model.metadata.MessageMetadata
import no.nav.tsm.sykmelding.input.core.model.metadata.OrgIdType

class OppgaveService(
    private val pdlClient: PdlClient,
    private val tsmOppgaveClient: TsmOppgaveClient,
    private val kafkaProducer: KafkaRecordProducer<OpprettOppgaveKafkaMessage>,
    env: Environment,
) {
    val logger = logger()
    val cluster = env.runtime.env

    suspend fun createOppgave(sykmeldingRecord: SykmeldingRecord) {
        val person =
            pdlClient.getPerson(sykmeldingRecord.sykmelding.pasient.fnr).getOrElse {
                throw RuntimeException("Fant ikke person for sykmelding: ${sykmeldingRecord.sykmelding.id}")
            }
        val oppgave = toOppgaveKafkaMessage(person, sykmeldingRecord)

        /*if (cluster == RuntimeCluster.DEV) {
            logger.info("creating tsm oppgave for sykmelding ${sykmeldingRecord.sykmelding.id}")
            val sykmeldingId = sykmeldingRecord.sykmelding.id
            val oppgaveRequest = toOppgaveRequest(sykmeldingId, person)
            tsmOppgaveClient.createOppgave(sykmeldingId, oppgaveRequest)
        } else {
            kafkaProducer.send(oppgave.messageId, oppgave)
        }*/
        kafkaProducer.send(oppgave.messageId, oppgave)
    }
}

private fun toOppgaveRequest(sykmeldingId: String, person: PdlPerson): OpprettOppgaveRequest {
    return OpprettOppgaveRequest(
        personident = person.identer.first { !it.historisk && it.gruppe == PdlIdentgruppe.AKTORID }.ident,
        beskrivelse =
            "Manuell behandling av sykmelding grunnet følgende regler: Sykmelding inneholder behandlingsdager (felt 4.4).",
        fristFerdigstillelse = finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4)),
        sykmeldingId = sykmeldingId,
        type = "BEHANDLINGSDAGER",
    )
}

private fun toOppgaveKafkaMessage(
    person: PdlPerson,
    sykmeldingRecord: SykmeldingRecord,
): OpprettOppgaveKafkaMessage {
    return OpprettOppgaveKafkaMessage(
        messageId = sykmeldingRecord.sykmelding.id,
        aktoerId = person.identer.first { !it.historisk && it.gruppe == PdlIdentgruppe.AKTORID }.ident,
        tildeltEnhetsnr = "",
        opprettetAvEnhetsnr = "9999",
        behandlesAvApplikasjon = "FS22",
        orgnr =
            when (val record = sykmeldingRecord) {
                is SykmeldingRecord.Digital -> record.metadata.orgnummer
                is SykmeldingRecord.Xml ->
                    when (val meta = record.metadata) {
                        is MessageMetadata.Xml.Emottak.EDI ->
                            meta.sender.underOrganisasjon?.ids?.firstOrNull { it.type == OrgIdType.ENH }?.id
                                ?: meta.sender.ids.firstOrNull { it.type == OrgIdType.ENH }?.id
                                ?: ""
                        else -> ""
                    }
                else -> ""
            },
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
            DateTimeFormatter.ISO_DATE.format(finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4))),
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
