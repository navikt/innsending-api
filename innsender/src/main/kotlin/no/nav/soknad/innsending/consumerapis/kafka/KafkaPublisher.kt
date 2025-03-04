package no.nav.soknad.innsending.consumerapis.kafka

import no.nav.soknad.innsending.config.KafkaConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class KafkaPublisher(
	private val kafkaConfig: KafkaConfig,
	private val kvitteringsSideTemplate: KafkaTemplate<String, String>
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	private val MESSAGE_ID = "MESSAGE_ID"

	fun publishToKvitteringsSide(key: String, value: String) {
		val topic = kafkaConfig.topics.kvitteringsSideTopic
		logger.info("$key: shall publish to kvitteringsside via topic $topic")
		publish(topic, key, value, kvitteringsSideTemplate)
		logger.info("$key: published to topic $topic")
	}

	private fun <K, V> publish(topic: String, key: K, value: V, kafkaTemplate: KafkaTemplate<K, V>) {
		val producerRecord = ProducerRecord(topic, key, value)
		val headers = RecordHeaders()
		headers.add(MESSAGE_ID, UUID.randomUUID().toString().toByteArray())
		headers.forEach { h -> producerRecord.headers().add(h) }

		val future = kafkaTemplate.send(producerRecord)
		future.get(10, TimeUnit.SECONDS)
	}


}
