package no.nav.tsm.modules.behandlingsdager.oppgave

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.tsm.core.Environment
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.Properties
import kotlin.collections.set

class OppgaveProducer(env: Environment) {
    private val log = LoggerFactory.getLogger(OppgaveProducer::class.java)
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    }

    private val kafkaProducer: KafkaProducer<String, ByteArray>
    private val topic = "teamsykmelding.oppgave-produser-oppgave"
    init {
        val kafkaProperties = Properties(env.kafka.config)

        kafkaProperties[ProducerConfig.ACKS_CONFIG] = "all"
        kafkaProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = "true"
        kafkaProperties[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = "1"
        kafkaProperties[ProducerConfig.RETRIES_CONFIG] = "5"
        kafkaProperties[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "gzip"

        kafkaProducer = KafkaProducer<String, ByteArray>(kafkaProperties, StringSerializer(), ByteArraySerializer())
    }

    fun send(opprettOppgaveKafkaMessage: OpprettOppgaveKafkaMessage) {
        log.info("Sending OpprettOppgave to kafka topic $topic")
        kafkaProducer.send(ProducerRecord(topic, opprettOppgaveKafkaMessage.messageId, objectMapper.writeValueAsBytes(opprettOppgaveKafkaMessage))).get()
    }
}