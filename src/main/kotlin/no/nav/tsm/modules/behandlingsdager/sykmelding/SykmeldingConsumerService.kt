package no.nav.tsm.modules.behandlingsdager.sykmelding

import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.*
import no.nav.tsm.core.Environment
import no.nav.tsm.core.logger
import no.nav.tsm.modules.behandlingsdager.oppgave.OppgaveService
import no.nav.tsm.sykmelding.input.core.model.Aktivitet
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.sykmelding.input.core.model.sykmeldingObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class SykmeldingConsumerService(val sykmeldingConsumer: SykmeldingConsumer,
                                val oppgaveService: OppgaveService,
                                val env: Environment,
) {

    private val log = logger()
    private val behandlingsdager = env.behandlingsdagerIds

    init {
        log.info("Behandlingsdager size is ${behandlingsdager.size}")
    }
    suspend fun start() = withContext(Dispatchers.IO) {
        while(isActive) {
            sykmeldingConsumer.subscribe()
            try {
                while (isActive) {
                    val records = sykmeldingConsumer.poll()
                    records.forEach { record ->
                        processRecord(record)
                    }
                    if(!records.isEmpty) {
                        sykmeldingConsumer.commitSync(records.nextOffsets())
                    }
                }
            } catch (cancellation: CancellationException) {
                log.info("SykmeldingConsumer cancelled gracefully (application stopping)", cancellation)
            } catch (ex: Exception) {
                sykmeldingConsumer.unsubscribe()
                log.error("Error running Kafka consumer, waiting 60 seconds to retry", ex)
                delay(60.seconds)
            }
        }

        withContext(NonCancellable) { sykmeldingConsumer.unsubscribe() }
    }

    private suspend fun processRecord(record: ConsumerRecord<String, ByteArray>) {
        val sykmeldingRecord = sykmeldingObjectMapper.readValue<SykmeldingRecord>(record.value())


        if (sykmeldingRecord is SykmeldingRecord.Digital || (sykmeldingRecord is SykmeldingRecord.Xml && behandlingsdager.contains(sykmeldingRecord.sykmelding.id) )) {

            val behandlingsdager = sykmeldingRecord.sykmelding.aktivitet.filterIsInstance<Aktivitet.Behandlingsdager>()
            if(behandlingsdager.isEmpty()) {
                log.info("${sykmeldingRecord.sykmelding.type} sykmelding is not behandlingsdager ${sykmeldingRecord.sykmelding.id}")
                return
            }

            log.info("${sykmeldingRecord.sykmelding.type} wiht id: ${sykmeldingRecord.sykmelding.id} er enkeltstående behandlingsdager, oppretter oppgave")

            oppgaveService.createOppgave(sykmeldingRecord)
        }

        sykmeldingConsumer.commitSync(record.partition(), record.offset() + 1)
    }
}