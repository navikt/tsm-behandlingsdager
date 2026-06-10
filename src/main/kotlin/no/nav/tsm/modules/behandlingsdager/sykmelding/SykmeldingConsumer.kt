package no.nav.tsm.modules.behandlingsdager.sykmelding

import no.nav.tsm.core.Environment
import no.nav.tsm.core.logger
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import java.time.Duration
import java.util.*
import kotlin.time.toJavaDuration

class SykmeldingConsumer(env: Environment) {
    private val kafkaConsumer: KafkaConsumer<String, ByteArray>
    private val groupId = "tsm-behandlingsdager-consumer"
    private val pollInterval: Duration = env.kafka.pollInterval.toJavaDuration()
    private val topic = "tsm.sykmeldinger"
    private val logger = logger()
    init {
        val kafkaProperties = Properties(env.kafka.config).apply {
            this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
            this[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        }
        kafkaConsumer = KafkaConsumer(kafkaProperties, StringDeserializer(), ByteArrayDeserializer())
    }

    fun subscribe() {
        logger.info("Subscribing $topic")
        kafkaConsumer.subscribe(listOf(topic))
    }

    fun unsubscribe() {
        logger.info("Unsubscribing $topic")
        kafkaConsumer.unsubscribe()
    }


    fun poll(): ConsumerRecords<String, ByteArray> {
        return kafkaConsumer.poll(pollInterval)
    }

    fun commitSync(partition: Int, currentOffset: Long) {
        kafkaConsumer.commitSync(mapOf(TopicPartition(topic, partition) to OffsetAndMetadata(currentOffset)))
    }

    fun commitSync(nextOffsets: Map<TopicPartition, OffsetAndMetadata>) {
        kafkaConsumer.commitSync(nextOffsets)
    }
}