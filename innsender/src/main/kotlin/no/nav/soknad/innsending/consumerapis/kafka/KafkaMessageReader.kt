package no.nav.soknad.innsending.consumerapis.kafka

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.soknad.innsending.config.KafkaConfig
import no.nav.soknad.innsending.repository.ArkiveringsStatus
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import javax.annotation.PostConstruct

@Service
@Profile("prod | dev")
class KafkaMessageReader(
	private val kafkaConfig: KafkaConfig,
	private val soknadRepository: SoknadRepository,
	private val innsenderMetrics: InnsenderMetrics
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@PostConstruct
	fun startKafka() {
		val job = GlobalScope.launch {
			readMessages()
		}
		logger.info("Kafka: Startet polling av ${kafkaConfig.topics} med job ${job.key}")
	}

	private fun readMessages() {
		logger.info("Kafka: Start konsumering av topic ${kafkaConfig.topics.messageTopic} med gruppeId ${kafkaConfig.applicationId}")

		KafkaConsumer<String, String>(kafkaConfigMap()).use { it ->
			it.subscribe(listOf(kafkaConfig.topics.messageTopic))
			while (true) {
				val messages = it.poll(Duration.ofMillis(5000))
				for (message in messages) {
					val key = message.key()
					if (message.value().startsWith("**Archiving: OK")) {
						logger.debug("$key: er arkivert")
						soknadRepository.updateArkiveringsStatus(ArkiveringsStatus.Arkivert, listOf(key))
					} else if (message.value().startsWith("**Archiving: FAILED")) {
						logger.error("$key: arkivering feilet")
						soknadRepository.updateArkiveringsStatus(ArkiveringsStatus.ArkiveringFeilet, listOf(key))
					}
				}
				it.commitSync()
				logger.debug("**Ferdig behandlet mottatte meldinger.")
			}
		}
	}


	private fun kafkaConfigMap(): MutableMap<String, Any> {
		return HashMap<String, Any>().also {
			it[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = kafkaConfig.brokers
			it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
			it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
			it[ConsumerConfig.GROUP_ID_CONFIG] = kafkaConfig.applicationId
			it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
			it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 5000
			if (kafkaConfig.security.enabled == "TRUE") {
				it[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = kafkaConfig.security.protocol
				it[SslConfigs.SSL_KEYSTORE_TYPE_CONFIG] = "PKCS12"
				it[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = kafkaConfig.security.trustStorePath
				it[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = kafkaConfig.security.trustStorePassword
				it[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = kafkaConfig.security.keyStorePath
				it[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = kafkaConfig.security.keyStorePassword
				it[SslConfigs.SSL_KEY_PASSWORD_CONFIG] = kafkaConfig.security.keyStorePassword
			}
		}
	}


}
