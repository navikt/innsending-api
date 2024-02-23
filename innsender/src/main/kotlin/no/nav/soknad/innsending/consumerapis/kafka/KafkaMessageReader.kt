package no.nav.soknad.innsending.consumerapis.kafka


import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
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
@Profile("prod | dev")
class KafkaMessageReader(
	private val repo: RepositoryUtils
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@KafkaListener(
		topics = ["\${kafka.topics.messageTopic}"],
		groupId = "\${kafka.applicationId}",
		containerFactory = "kafkaListenerContainerFactory"
	)
	fun listen(@Payload message: String, @Header(KafkaHeaders.RECEIVED_KEY) messageKey: String, ack: Acknowledgment) {
		// Soknadsarkiverer legger på melding om arkiveringsstatus for både søknader sendt inn av sendsoknad og innsending-api
		// Henter fra databasen for å oppdatere arkiveringsstatus for søknader sendt inn av innsending-api
		try {
			logger.info("Kafka: henter søknad $messageKey fra database")

			val soknad = repo.hentSoknadDb(messageKey)

			logger.info("Kafka: hentet søknad ${soknad.innsendingsid} fra database")

			if (message.startsWith("**Archiving: OK")) {
				logger.info("$messageKey: er arkivert")
				repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.Arkivert)
				loggAntallAvHendelsetype(HendelseType.Arkivert)
			} else if (message.startsWith("**Archiving: FAILED")) {
				logger.error("$messageKey: arkivering feilet")
				repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.ArkiveringFeilet)
				loggAntallAvHendelsetype(HendelseType.ArkiveringFeilet)
			}
		} catch (ex: ResourceNotFoundException) {
			logger.info("Kafka: fant ikke søknad med key $messageKey i database. Mest sannsynlig en søknad sendt inn av sendsoknad")
			ack.acknowledge()
		} catch (ex: Exception) {
			logger.warn("Kafka exception: ${ex.message}", ex)
			ack.acknowledge()
		}

		logger.info("Kafka: Ferdig behandlet mottatt melding med key $messageKey")
		ack.acknowledge()

	}

	private fun loggAntallAvHendelsetype(hendelseType: HendelseType) {
		logger.info("Antall søknader med hendelsetype $hendelseType = ${repo.findNumberOfEventsByType(hendelseType)}")
	}

}

