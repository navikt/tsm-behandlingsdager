package no.nav.tsm.utils

import no.nav.tsm.core.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.testcontainers.kafka.ConfluentKafkaContainer
import java.util.*
import kotlin.time.Duration.Companion.seconds


abstract class WithKafka {
    companion object {
        val kafka: ConfluentKafkaContainer =
            ConfluentKafkaContainer("confluentinc/cp-kafka:8.1.0").apply {
                start()
            }

        fun integrationEnvironment(): Environment {
            val config =
                Properties().apply {
                    this[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafka.bootstrapServers
                    this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] =
                        StringDeserializer::class.java.name
                    this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
                        ByteArrayDeserializer::class.java.name
                    this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
                    this[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
                    this[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                }
            return Environment(
                runtime = Runtime(env = RuntimeEnvironments.DEV, name = "tsm-behandlingsdager-it"),
                kafka = KafkaConfig(config = config, pollInterval = 0.seconds),
                external =
                    ExternalConfig(
                        texasConfig = TexasConfig(tokenEndpoint = "https://test.token/token"),
                        tsmPdlCache = TsmPdlConfig(url = "https://test.pdl"),
                    ),
            )
        }
    }
}
