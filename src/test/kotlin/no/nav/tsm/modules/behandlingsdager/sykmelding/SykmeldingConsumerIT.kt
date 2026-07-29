package no.nav.tsm.modules.behandlingsdager.sykmelding

import arrow.core.right
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.matchers.equals.shouldEqual
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import no.nav.tsm.modules.behandlingsdager.oppgave.OppgaveProducer
import no.nav.tsm.modules.behandlingsdager.oppgave.OppgaveService
import no.nav.tsm.modules.behandlingsdager.oppgave.OpprettOppgaveKafkaMessage
import no.nav.tsm.modules.behandlingsdager.pdl.PdlClient
import no.nav.tsm.sykmelding.input.core.model.RuleType
import no.nav.tsm.sykmelding.input.core.model.sykmeldingObjectMapper
import no.nav.tsm.utils.TestData
import no.nav.tsm.utils.WithKafka
import no.nav.tsm.utils.testJsonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer

class SykmeldingConsumerIT : WithKafka() {

    private val sykmeldingTopic: String = "tsm.sykmeldinger"
    private val oppgaveTopic: String = "teamsykmelding.oppgave-produser-oppgave"
    private val oppgaveConsumer = oppgaveTestConsumer().also { it.subscribe(listOf(oppgaveTopic)) }
    val env = integrationEnvironment()
    private val oppgaveProducer = spyk(OppgaveProducer(env))
    private val sykmeldingConsumer = spyk(SykmeldingConsumer(env))

    private val pdlClient: PdlClient =
        mockk<PdlClient>().also {
            coEvery { it.getPerson(any()) } returns TestData.pdlPerson().right()
        }

    val service =
        SykmeldingConsumerService(
            sykmeldingConsumer,
            OppgaveService(pdlClient, oppgaveProducer),
            env,
        )
    private val sykmeldingProducer = KafkaProducer(env.kafka.config, StringSerializer(), ByteArraySerializer())

    @AfterTest
    fun tearDown() {
        oppgaveConsumer.close()
    }

    @Test
    fun `produces oppgave for a sykmelding with behandlingsdager`() {
        runWithSykmeldingConsumer {
            val sykmeldingId = UUID.randomUUID().toString()
            val record =
                TestData.digitalSykmeldingRecord(
                    sykmeldingId = sykmeldingId,
                    aktivitet = listOf(TestData.behandlingsdagerAktivitet()),
                )

            val recordMetadata = publishInput(record.sykmelding.id, record)
            val id = oppgaveConsumUntil(record.sykmelding.id, 1000L)

            id shouldBe sykmeldingId
            verify(exactly = 1, timeout = 10000) {
                sykmeldingConsumer.commitSync(getNextOffsets(listOf(recordMetadata)))
            }
            verify(exactly = 1, timeout = 1000) {
                sykmeldingConsumer.commitSync(0, recordMetadata.offset() + 1)
            }
        }
    }

    @Test
    fun `does not produce an oppgave for a digital sykmelding without behandlingsdager`() {
        runWithSykmeldingConsumer {
            val record = TestData.digitalSykmeldingRecord(aktivitet = listOf(TestData.gradertAktivitet()))

            val recordMetadata = publishInput(record.sykmelding.id, record)
            verify(exactly = 1, timeout = 1000) {
                sykmeldingConsumer.commitSync(getNextOffsets(listOf(recordMetadata)))
            }
            verify(exactly = 0) { sykmeldingConsumer.commitSync(0, recordMetadata.offset()) }

            verify(exactly = 0) {
                oppgaveProducer.send(match { it.messageId == record.sykmelding.id })
            }
        }
    }

    @Test
    fun `does not produce an oppgave for a digital sykmelding behandlingsdager when status is not OK`() {
        runWithSykmeldingConsumer {
            val okRecord = TestData.digitalSykmeldingRecord(aktivitet = listOf(TestData.behandlingsdagerAktivitet()))
            val record = okRecord.copy(validation = okRecord.validation.copy(status = RuleType.PENDING))
            val recordMetadata = publishInput(record.sykmelding.id, record)
            verify(exactly = 1, timeout = 1000) {
                sykmeldingConsumer.commitSync(getNextOffsets(listOf(recordMetadata)))
            }
            verify(exactly = 0) { sykmeldingConsumer.commitSync(0, recordMetadata.offset()) }

            verify(exactly = 0) {
                oppgaveProducer.send(match { it.messageId == record.sykmelding.id })
            }
        }
    }

    @Test
    fun `should commit offsets only of behandlingsdager in records`() = runWithSykmeldingConsumer {
        val sykmeldingRecords =
            (0 until 10).map {
                val aktivitet =
                    if (it % 2 == 0) {
                        TestData.behandlingsdagerAktivitet()
                    } else TestData.aktivitetIkkeMulig()

                TestData.digitalSykmeldingRecord(
                    sykmeldingId = UUID.randomUUID().toString(),
                    aktivitet = listOf(aktivitet),
                )
            }
        val recordsMetadata = sykmeldingRecords.map { digital ->
            publishInput(digital.sykmelding.id, digital)
        }

        val nextOffsets =
            recordsMetadata.map { it.offset() }.filterIndexed { index, _ -> (index % 2) == 0 }.map { it + 1 }

        recordsMetadata.forEach {
            verify(exactly = 1, timeout = 10000) {
                sykmeldingConsumer.commitSync(getNextOffsets(listOf(it)))
            }
        }

        verifyOrder { nextOffsets.sorted().forEach { sykmeldingConsumer.commitSync(0, it) } }
    }

    @Test
    fun `does produce an oppgave for an xml sykmelding with behandlingsdager`() {
        runWithSykmeldingConsumer {
            val record = TestData.xmlSykmeldingRecord(aktivitet = listOf(TestData.behandlingsdagerAktivitet()))

            val recordMetadata = publishInput(record.sykmelding.id, record)
            verify(exactly = 1, timeout = 10000) {
                sykmeldingConsumer.commitSync(getNextOffsets(listOf(recordMetadata)))
            }
            verify(exactly = 1) { sykmeldingConsumer.commitSync(0, recordMetadata.offset() + 1) }

            verify(exactly = 1) {
                oppgaveProducer.send(match { it.messageId == record.sykmelding.id })
            }
        }
    }

    @Test
    fun `produce an oppgave for an xml sykmelding with behandlingsdager`() {
        runWithSykmeldingConsumer {
            val record =
                TestData.xmlSykmeldingRecord(
                    sykmeldingId = "1",
                    aktivitet = listOf(TestData.behandlingsdagerAktivitet()),
                )

            val recordMetadata = publishInput(record.sykmelding.id, record)
            val id = oppgaveConsumUntil(record.sykmelding.id, 1000L)
            id shouldEqual "1"
            verify(exactly = 1, timeout = 10000) {
                sykmeldingConsumer.commitSync(getNextOffsets(listOf(recordMetadata)))
            }
            verify(exactly = 1, timeout = 1000) {
                sykmeldingConsumer.commitSync(0, recordMetadata.offset() + 1)
            }
        }
    }

    @Test
    fun `test correct offset commiting`() {

        runWithSykmeldingConsumer {
            val record = TestData.digitalSykmeldingRecord(aktivitet = listOf(TestData.behandlingsdagerAktivitet()))
            val recordMetadata = publishInput(record.sykmelding.id, record)

            verify(exactly = 1, timeout = 1000) {
                sykmeldingConsumer.commitSync(getNextOffsets(listOf(recordMetadata)))
            }
            verify(exactly = 1) {
                sykmeldingConsumer.commitSync(
                    recordMetadata.partition(),
                    recordMetadata.offset() + 1,
                )
            }
        }

        runWithSykmeldingConsumer {
            val record = TestData.digitalSykmeldingRecord(aktivitet = listOf(TestData.behandlingsdagerAktivitet()))

            val recordMetadata = publishInput(record.sykmelding.id, record)
            verify(exactly = 1, timeout = 1000) {
                sykmeldingConsumer.commitSync(getNextOffsets(listOf(recordMetadata)))
            }
            verify(exactly = 1) {
                sykmeldingConsumer.commitSync(
                    recordMetadata.partition(),
                    recordMetadata.offset() + 1,
                )
            }
            verify(exactly = 1) {
                sykmeldingConsumer.commitSync(recordMetadata.partition(), recordMetadata.offset())
            }
        }
    }

    private fun oppgaveTestConsumer(): KafkaConsumer<String, ByteArray> {
        val props =
            Properties().apply {
                this[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafka.bootstrapServers
                this[ConsumerConfig.GROUP_ID_CONFIG] = "behandlingsdager-it"
                this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "true"
            }
        return spyk(KafkaConsumer(props, StringDeserializer(), ByteArrayDeserializer()))
    }

    private suspend fun publishInput(
        key: String,
        record: no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord,
    ) =
        withContext(Dispatchers.IO) {
            sykmeldingProducer
                .send(
                    ProducerRecord(
                        sykmeldingTopic,
                        key,
                        sykmeldingObjectMapper.writeValueAsBytes(record),
                    )
                )
                .get()
        }

    private suspend fun oppgaveConsumUntil(id: String, ms: Long): String? =
        withContext(Dispatchers.IO) {
            var consumedId: String? = null
            withTimeout(ms.milliseconds) {
                while (consumedId != id) {
                    consumedId =
                        oppgaveConsumer
                            .poll(0.seconds.toJavaDuration())
                            .map {
                                testJsonObjectMapper.readValue<OpprettOppgaveKafkaMessage>(it.value()).messageId
                            }
                            .firstOrNull { it == id }
                }
            }
            consumedId
        }

    private fun runWithSykmeldingConsumer(block: suspend CoroutineScope.() -> Unit) = runTest {
        val job = launch { service.start() }
        block()
        job.cancelAndJoin()
    }

    private fun getNextOffsets(recordMetadata: List<RecordMetadata>): Map<TopicPartition, OffsetAndMetadata> =
        recordMetadata.associate {
            TopicPartition(it.topic(), it.partition()) to OffsetAndMetadata(it.offset() + 1, Optional.of(0), "")
        }
}
