package no.nav.soknad.innsending.consumerapis.kafka

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.soknad.innsending.config.KafkaConfig
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.service.RepositoryUtils
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

@Service
@Profile("prod | dev")
class KafkaMessageReader(
	private val kafkaConfig: KafkaConfig,
	private val repo: RepositoryUtils
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

					// Soknadsarkiverer legger på melding om arkiveringsstatus for både søknader sendt inn av sendsoknad og innsending-api
					// Henter fra databasen for å oppdatere arkiveringsstatus for søknader sendt inn av innsending-api
					try {
						logger.info("Kafka: henter søknad $key fra database")

						val soknad = repo.hentSoknadDb(key)

						logger.info("Kafka: hentet søknad ${soknad.innsendingsid} fra database")

						if (message.value().startsWith("**Archiving: OK")) {
							logger.info("$key: er arkivert")
							repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.Arkivert)
							loggAntallAvHendelsetype(HendelseType.Arkivert)
						} else if (message.value().startsWith("**Archiving: FAILED")) {
							logger.error("$key: arkivering feilet")
							repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.ArkiveringFeilet)
							loggAntallAvHendelsetype(HendelseType.ArkiveringFeilet)
						}
					} catch (ex: Exception) {
						logger.warn("Kafka exception: ${ex.message}", ex)
					}

				}
				it.commitSync()
				logger.info("Kafka: Ferdig behandlet mottatte meldinger.")
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
			it[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = "false"
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

	private fun loggAntallAvHendelsetype(hendelseType: HendelseType) {
		logger.info("Antall søknader med hendelsetype $hendelseType = ${repo.findNumberOfEventsByType(hendelseType)}")
	}

}
