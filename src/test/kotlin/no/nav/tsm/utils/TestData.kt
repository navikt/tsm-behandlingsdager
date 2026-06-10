package no.nav.tsm.utils

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.tsm.modules.behandlingsdager.pdl.PdlIdent
import no.nav.tsm.modules.behandlingsdager.pdl.PdlIdentgruppe
import no.nav.tsm.modules.behandlingsdager.pdl.PdlPerson
import no.nav.tsm.sykmelding.input.core.model.Aktivitet
import no.nav.tsm.sykmelding.input.core.model.ArbeidsgiverInfo
import no.nav.tsm.sykmelding.input.core.model.AvsenderSystem
import no.nav.tsm.sykmelding.input.core.model.Behandler
import no.nav.tsm.sykmelding.input.core.model.MedisinskVurdering
import no.nav.tsm.sykmelding.input.core.model.Pasient
import no.nav.tsm.sykmelding.input.core.model.RuleType
import no.nav.tsm.sykmelding.input.core.model.Sykmelder
import no.nav.tsm.sykmelding.input.core.model.Sykmelding
import no.nav.tsm.sykmelding.input.core.model.SykmeldingMeta
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.sykmelding.input.core.model.ValidationResult
import no.nav.tsm.sykmelding.input.core.model.metadata.HelsepersonellKategori
import no.nav.tsm.sykmelding.input.core.model.metadata.Meldingstype
import no.nav.tsm.sykmelding.input.core.model.metadata.MessageInfo
import no.nav.tsm.sykmelding.input.core.model.metadata.MessageMetadata
import no.nav.tsm.sykmelding.input.core.model.metadata.Navn
import no.nav.tsm.sykmelding.input.core.model.metadata.PersonId
import no.nav.tsm.sykmelding.input.core.model.metadata.PersonIdType

object TestData {

    const val DEFAULT_FNR = "12345678901"
    const val DEFAULT_AKTORID = "12345678912345"
    const val DEFAULT_ORGNR = "987654321"

    fun pdlPerson(
        aktorId: String? = DEFAULT_AKTORID,
        aktorIdHistorisk: Boolean = false,
        fnr: String = DEFAULT_FNR,
    ): PdlPerson {
        val identer =
            buildList {
                add(
                    PdlIdent(
                        ident = fnr,
                        gruppe = PdlIdentgruppe.FOLKEREGISTERIDENT,
                        historisk = false,
                    )
                )
                if (aktorId != null) {
                    add(
                        PdlIdent(
                            ident = aktorId,
                            gruppe = PdlIdentgruppe.AKTORID,
                            historisk = aktorIdHistorisk,
                        )
                    )
                }
            }
        return PdlPerson(foedselsdato = LocalDate.parse("1990-01-01"), identer = identer)
    }

    fun behandlingsdagerAktivitet(
        fom: LocalDate = LocalDate.now(),
        tom: LocalDate = LocalDate.now().plusDays(7),
        antall: Int = 1,
    ): Aktivitet.Behandlingsdager =
        Aktivitet.Behandlingsdager(antallBehandlingsdager = antall, fom = fom, tom = tom)

    fun gradertAktivitet(
        fom: LocalDate = LocalDate.now(),
        tom: LocalDate = LocalDate.now().plusDays(7),
    ): Aktivitet.Gradert =
        Aktivitet.Gradert(grad = 50, fom = fom, tom = tom, reisetilskudd = false)

    fun aktivitetIkkeMulig(fom: LocalDate = LocalDate.now(), tom: LocalDate = LocalDate.now().plusDays(7)
    ) : Aktivitet.IkkeMulig = Aktivitet.IkkeMulig(fom = fom, tom = tom, medisinskArsak = null, arbeidsrelatertArsak = null)
    fun digitalSykmeldingRecord(
        sykmeldingId: String = UUID.randomUUID().toString(),
        fnr: String = DEFAULT_FNR,
        orgnummer: String = DEFAULT_ORGNR,
        aktivitet: List<Aktivitet> = listOf(behandlingsdagerAktivitet()),
    ): SykmeldingRecord.Digital {
        val now = OffsetDateTime.now()
        return SykmeldingRecord.Digital(
            metadata = MessageMetadata.Digital(orgnummer = orgnummer),
            sykmelding =
                Sykmelding.Digital(
                    id = sykmeldingId,
                    metadata =
                        SykmeldingMeta.Digital(
                            mottattDato = now,
                            genDate = now,
                            avsenderSystem = AvsenderSystem(navn = "test", versjon = "1"),
                        ),
                    pasient =
                        Pasient(
                            navn = null,
                            navKontor = null,
                            navnFastlege = null,
                            fnr = fnr,
                            kontaktinfo = emptyList(),
                        ),
                    medisinskVurdering =
                        MedisinskVurdering.Digital(
                            hovedDiagnose = null,
                            biDiagnoser = null,
                            svangerskap = false,
                            yrkesskade = null,
                            skjermetForPasient = false,
                            annenFravarsgrunn = null,
                        ),
                    aktivitet = aktivitet,
                    behandler =
                        Behandler(
                            navn = Navn("Lege", null, "Legesen"),
                            adresse = null,
                            ids = listOf(PersonId(id = "9144889", type = PersonIdType.HPR)),
                            kontaktinfo = emptyList(),
                        ),
                    sykmelder =
                        Sykmelder(
                            ids = listOf(PersonId(id = "9144889", type = PersonIdType.HPR)),
                            helsepersonellKategori = HelsepersonellKategori.LEGE,
                        ),
                    arbeidsgiver = ArbeidsgiverInfo.Ingen(),
                    tilbakedatering = null,
                    bistandNav = null,
                    utdypendeSporsmal = null,
                ),
            validation =
                ValidationResult(status = RuleType.OK, timestamp = now, rules = emptyList()),
        )
    }

    fun xmlSykmeldingRecord(
        sykmeldingId: String = UUID.randomUUID().toString(),
        fnr: String = DEFAULT_FNR,
        aktivitet: List<Aktivitet> = listOf(behandlingsdagerAktivitet()),
    ): SykmeldingRecord.Xml {
        val now = OffsetDateTime.now()
        return SykmeldingRecord.Xml(
            metadata =
                MessageMetadata.Xml.Egenmeldt(
                    msgInfo =
                        MessageInfo(
                            type = Meldingstype.SYKMELDING,
                            genDate = now,
                            msgId = UUID.randomUUID().toString(),
                            migVersjon = null,
                        )
                ),
            sykmelding =
                Sykmelding.Xml(
                    id = sykmeldingId,
                    metadata =
                        SykmeldingMeta.Legacy(
                            mottattDato = now,
                            genDate = now,
                            avsenderSystem = AvsenderSystem(navn = "test", versjon = "1"),
                            behandletTidspunkt = now,
                            regelsettVersjon = null,
                            strekkode = null,
                        ),
                    pasient =
                        Pasient(
                            navn = null,
                            navKontor = null,
                            navnFastlege = null,
                            fnr = fnr,
                            kontaktinfo = emptyList(),
                        ),
                    medisinskVurdering =
                        MedisinskVurdering.Legacy(
                            hovedDiagnose = null,
                            biDiagnoser = null,
                            svangerskap = false,
                            yrkesskade = null,
                            skjermetForPasient = false,
                            syketilfelletStartDato = null,
                            annenFraversArsak = null,
                        ),
                    aktivitet = aktivitet,
                    arbeidsgiver = ArbeidsgiverInfo.Ingen(),
                    behandler =
                        Behandler(
                            navn = Navn("Lege", null, "Legesen"),
                            adresse = null,
                            ids = listOf(PersonId(id = "9144889", type = PersonIdType.HPR)),
                            kontaktinfo = emptyList(),
                        ),
                    sykmelder =
                        Sykmelder(
                            ids = listOf(PersonId(id = "9144889", type = PersonIdType.HPR)),
                            helsepersonellKategori = HelsepersonellKategori.LEGE,
                        ),
                    prognose = null,
                    tiltak = null,
                    bistandNav = null,
                    tilbakedatering = null,
                    utdypendeOpplysninger = null,
                ),
            validation =
                ValidationResult(status = RuleType.OK, timestamp = now, rules = emptyList()),
        )
    }
}
