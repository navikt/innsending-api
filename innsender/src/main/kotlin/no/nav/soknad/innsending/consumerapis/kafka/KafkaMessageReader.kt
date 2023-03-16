package no.nav.soknad.innsending.consumerapis.kafka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.config.KafkaConfig
import no.nav.soknad.innsending.repository.SoknadRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import javax.annotation.PostConstruct
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration

@Service
@Profile("prod | dev")
class KafkaMessageReader(
	private val kafkaConfig: KafkaConfig,
	private val soknadRepository: SoknadRepository) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@PostConstruct
	fun startKafka() {
		val job = GlobalScope.launch {
			readMessages()
		}
		logger.info("Startet polling av ${kafkaConfig.topics} med job ${job.key}")
	}

	private fun readMessages() {
		val topic = kafkaConfig.topics.messageTopic
		val groupId = kafkaConfig.applicationId

		val props = Properties().apply {
			put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.brokers)
			put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
			put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
			put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
			put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
		}
		KafkaConsumer<String, String>(props).use { it ->
			it.subscribe(listOf(topic))
			while (true) {
				val messages = it.poll(Duration.ofMillis(5000))
				for (message in messages) {
					val key = message.key()
					if (message.value().startsWith("**Archiving: OK")) {
						soknadRepository.updateErArkivert(true, listOf(key))
					} else if (message.value().startsWith("**Archiving: FAILED")) {
						soknadRepository.updateErArkivert(false, listOf(key))
					}
				}
				it.commitSync()
			}
		}
	}

}
