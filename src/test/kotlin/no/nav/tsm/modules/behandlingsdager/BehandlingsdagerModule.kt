package no.nav.tsm.modules.behandlingsdager

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.equals.shouldEqual
import io.kotest.matchers.shouldBe
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import no.nav.tsm.ktor.kafka.producer.KafkaRecordProducer
import no.nav.tsm.ktor.kafka.test.KafkaContainer
import no.nav.tsm.modules.behandlingsdager.oppgave.OpprettOppgaveKafkaMessage
import no.nav.tsm.sykmelding.input.core.model.RuleType
import no.nav.tsm.sykmelding.input.core.model.SykmeldingRecord
import no.nav.tsm.sykmelding.input.core.model.sykmeldingObjectMapper
import no.nav.tsm.utils.TestData
import no.nav.tsm.utils.integrationEnvironment
import no.nav.tsm.utils.testJsonObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import tools.jackson.module.kotlin.readValue
import java.util.*
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class BehandlingsdagerModuleTest {
    val sykmeldingTopic = "tsm.sykmeldinger"
    val oppgaveTopic = "teamsykmelding.oppgave-produser-oppgave"

    val kafka = KafkaContainer(createTopics = listOf(sykmeldingTopic, oppgaveTopic))
    val env = integrationEnvironment()
    val producer = kafka.createAnythingProducer()
    val testOppgaveConsumer = oppgaveTestConsumer(kafka.config).also { it.subscribe(listOf(oppgaveTopic)) }

    @AfterTest
    fun tearDown() {
        testOppgaveConsumer.close()
    }

    private suspend fun ApplicationTestBuilder.configureTestApp(overrides: Application.() -> Unit = {}) {
        kafka.configureKafka(this)
        application.dependencies { provide { env } }

        application.overrides()
        application.configureBehandlingsdager()

        startApplication()
    }

    @Test
    fun `produces oppgave for a sykmelding with behandlingsdager`() = testApplication {
        configureTestApp()

        val sykmeldingId = UUID.randomUUID().toString()
        val record =
            TestData.digitalSykmeldingRecord(
                sykmeldingId = sykmeldingId,
                aktivitet = listOf(TestData.behandlingsdagerAktivitet()),
            )

        val recordMetadata = publishTestRecord(record.sykmelding.id, record)
        val id = oppgaveConsumUntil(record.sykmelding.id, 1000L)

        id shouldBe sykmeldingId
        eventually(2.seconds) {
            kafka.getOffset(sykmeldingTopic, "tsm-behandlingsdager-consumer") shouldEqual recordMetadata.offset() + 1
        }
    }

    @Test
    fun `does not produce an oppgave for a digital sykmelding without behandlingsdager`() = testApplication {
        val producerMock = mockk<KafkaRecordProducer<OpprettOppgaveKafkaMessage>>()
        configureTestApp {
            dependencies {
                provide { producerMock }
            }
        }

        val record = TestData.digitalSykmeldingRecord(aktivitet = listOf(TestData.gradertAktivitet()))
        val recordMetadata = publishTestRecord(record.sykmelding.id, record)

        eventually(duration = 5.seconds) {
            kafka.getOffset(sykmeldingTopic, "tsm-behandlingsdager-consumer") shouldEqual recordMetadata.offset() + 1
        }

        verify(exactly = 0) { producerMock.send(any(), any()) }
    }

    @Test
    fun `does not produce an oppgave for a digital sykmelding behandlingsdager when status is not OK`() =
        testApplication {
            val producerMock = mockk<KafkaRecordProducer<OpprettOppgaveKafkaMessage>>()
            configureTestApp {
                dependencies {
                    provide { producerMock }
                }
            }

            val okRecord = TestData.digitalSykmeldingRecord(aktivitet = listOf(TestData.behandlingsdagerAktivitet()))
            val record = okRecord.copy(validation = okRecord.validation.copy(status = RuleType.PENDING))
            val recordMetadata = publishTestRecord(record.sykmelding.id, record)

            eventually(duration = 5.seconds) {
                kafka.getOffset(
                    sykmeldingTopic,
                    "tsm-behandlingsdager-consumer",
                ) shouldEqual recordMetadata.offset() + 1
            }

            verify(exactly = 0) { producerMock.send(any(), any()) }
        }

    @Test
    fun `does produce an oppgave for an xml sykmelding with behandlingsdager`() = testApplication {
        configureTestApp()

        val sykmeldingId = UUID.randomUUID().toString()
        val record =
            TestData.xmlSykmeldingRecord(
                sykmeldingId = sykmeldingId,
                aktivitet = listOf(TestData.behandlingsdagerAktivitet()),
            )

        val recordMetadata = publishTestRecord(record.sykmelding.id, record)
        val id = oppgaveConsumUntil(record.sykmelding.id, 1000L)

        id shouldBe sykmeldingId
        eventually(2.seconds) {
            kafka.getOffset(sykmeldingTopic, "tsm-behandlingsdager-consumer") shouldEqual recordMetadata.offset() + 1
        }
    }

    @Test
    fun `produce an oppgave for an xml sykmelding with behandlingsdager`() = testApplication {
        configureTestApp()

        val sykmeldingId = UUID.randomUUID().toString()
        val record =
            TestData.xmlSykmeldingRecord(
                sykmeldingId = sykmeldingId,
                aktivitet = listOf(TestData.behandlingsdagerAktivitet()),
            )

        val recordMetadata = publishTestRecord(record.sykmelding.id, record)
        val id = oppgaveConsumUntil(record.sykmelding.id, 1000L)

        id shouldBe sykmeldingId
        kafka.getOffset(sykmeldingTopic, "tsm-behandlingsdager-consumer") shouldEqual recordMetadata.offset() + 1
    }

    private fun oppgaveTestConsumer(config: Map<String, String>): KafkaConsumer<String, ByteArray> {
        val props =
            config +
                mapOf(
                    ConsumerConfig.GROUP_ID_CONFIG to "behandlingsdager-it",
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "true",
                )

        return spyk(KafkaConsumer(props, StringDeserializer(), ByteArrayDeserializer()))
    }

    private suspend fun publishTestRecord(
        key: String,
        record: SykmeldingRecord,
    ) =
        withContext(Dispatchers.IO) {
            val record = ProducerRecord(sykmeldingTopic, key, sykmeldingObjectMapper.writeValueAsBytes(record))
            producer.send(record).get()
        }

    private suspend fun oppgaveConsumUntil(id: String, ms: Long): String? =
        withContext(Dispatchers.IO) {
            var consumedId: String? = null
            withTimeout(ms.milliseconds) {
                while (consumedId != id) {
                    consumedId =
                        testOppgaveConsumer
                            .poll(100.milliseconds.toJavaDuration())
                            .map {
                                testJsonObjectMapper.readValue<OpprettOppgaveKafkaMessage>(it.value()).messageId
                            }
                            .firstOrNull { it == id }
                }
            }
            consumedId
        }
}
