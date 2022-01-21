package no.nav.soknad.innsending.brukernotifikasjon.kafka

import java.util.concurrent.TimeUnit
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.soknad.innsending.config.KafkaConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.header.Headers
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("dev | prod")
class KafkaPublisher(private val kafkaConfig: KafkaConfig): KafkaPublisherInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val kafkaMessageProducer = KafkaProducer<Nokkel, Beskjed>(kafkaConfigMap())
	private val kafkaTaskProducer = KafkaProducer<Nokkel, Oppgave>(kafkaConfigMap())
	private val kafkaDoneProducer = KafkaProducer<Nokkel, Done>(kafkaConfigMap())

	override fun putApplicationMessageOnTopic(key: Nokkel, value: Beskjed, headers: Headers) {
		val topic = kafkaConfig.kafkaTopicBeskjed
		val kafkaProducer = kafkaMessageProducer
		putDataOnTopic(key, value, headers, topic, kafkaProducer)
	}

	override fun putApplicationTaskOnTopic(key: Nokkel, value: Oppgave, headers: Headers) {
		val topic = kafkaConfig.kafkaTopicOppgave
		val kafkaProducer = kafkaTaskProducer
		putDataOnTopic(key, value, headers, topic, kafkaProducer)
	}

	override fun putApplicationDoneOnTopic(key: Nokkel, value: Done, headers: Headers) {
		val topic = kafkaConfig.kafkaTopicDone
		val kafkaProducer = kafkaDoneProducer
		putDataOnTopic(key, value, headers, topic, kafkaProducer)
	}

	private fun <T> putDataOnTopic(key: Nokkel, value: T, headers: Headers, topic: String,
																 kafkaProducer: KafkaProducer<Nokkel, T>) {
		val producerRecord = ProducerRecord(topic, key, value)
		headers.forEach { h -> producerRecord.headers().add(h) }

		try {
			kafkaProducer
				.send(producerRecord)
				.get(9000, TimeUnit.MILLISECONDS) // Blocking call
		} catch (e: Throwable) {
			logger.error("Publisering av brukernotifikasjon feilet", e)
			throw e
		}
	}

	private fun kafkaConfigMap(): MutableMap<String, Any> {
		return HashMap<String, Any>().also {
			it[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = kafkaConfig.schemaRegistryUrl
			it[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.servers
			it[ProducerConfig.MAX_BLOCK_MS_CONFIG] = 4000
			it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
			it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = KafkaAvroSerializer::class.java
			if (kafkaConfig.secure == "TRUE") {
				it[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = kafkaConfig.protocol
				it[SaslConfigs.SASL_JAAS_CONFIG] = kafkaConfig.getSaslJaasConfig()
				it[SaslConfigs.SASL_MECHANISM] = kafkaConfig.salsmec
			}
		}
	}
}
