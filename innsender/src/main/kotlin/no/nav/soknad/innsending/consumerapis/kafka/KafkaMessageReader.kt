package no.nav.soknad.innsending.consumerapis.kafka


import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.service.RepositoryUtils
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service

@Service
@Profile("prod | dev | endtoend | loadtests")
class KafkaMessageReader(
	private val repo: RepositoryUtils
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@KafkaListener(
		topics = ["\${kafka.topics.arkiveringstilbakemeldingTopic}"],
		groupId = "\${kafka.applicationId}",
		containerFactory = "kafkaListenerContainerFactory"
	)
	fun listen(@Payload message: String, @Header(KafkaHeaders.RECEIVED_KEY) messageKey: String, ack: Acknowledgment) {
		// Soknadsarkiverer legger på melding om arkiveringsstatus.
		// Henter søknad fra databasen for å oppdatere arkiveringsstatus.
		try {
			logger.info("Kafka: henter søknad $messageKey fra database")

			val soknad = repo.hentSoknadDb(messageKey)

			logger.info("Kafka: hentet søknad ${soknad.innsendingsid} fra database")

			if (message.startsWith("**Archiving: OK")) {
				logger.info("$messageKey: er arkivert")
				repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.Arkivert)
			} else if (message.startsWith("**Archiving: FAILED")) {
				logger.error("$messageKey: arkivering feilet")
				repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.ArkiveringFeilet)
			}

			logger.info("Kafka: Ferdig behandlet mottatt melding med key $messageKey")
			ack.acknowledge()
		} catch (ex: ResourceNotFoundException) {
			logger.warn("Kafka: fant ikke søknad med key $messageKey i database.")
			ack.acknowledge()
		} catch (ex: Exception) {
			logger.warn("Kafka exception: ${ex.message}", ex)
		}
	}

}

