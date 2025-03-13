package no.nav.soknad.innsending.consumerapis.kafka

import no.nav.soknad.innsending.config.PublisherConfig
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.tms.soknad.event.SoknadEvent
import no.nav.tms.soknadskvittering.builder.SoknadEventBuilder
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service

@Service
@Profile("prod | dev | endtoend | loadtests  | test")
class KafkaMessageReader(
	private val repo: RepositoryUtils,
	private val kafkaPublisher: KafkaPublisher,
	private val publisherConfig: PublisherConfig
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
		var ok = false
		try {
			ok = handleArchivingEvents(message, messageKey)
		} finally {
			if (ok) ack.acknowledge()
		}
	}

	fun handleArchivingEvents(message: String, messageKey: String): Boolean {
		try {
			logger.info("Kafka: henter søknad $messageKey fra database")

			val soknad = repo.hentSoknadDb(messageKey)

			logger.info("Kafka: hentet søknad ${soknad.innsendingsid} fra database")

			if (message.startsWith("**Archiving: OK")) {
				logger.info("$messageKey: er arkivert")
				repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.Arkivert)
				publiserInnsendtSoknadOppdatering(soknad, message)
				loggAntallAvHendelsetype(HendelseType.Arkivert)
			} else if (message.startsWith("**Archiving: FAILED")) {
				logger.error("$messageKey: arkivering feilet")
				repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.ArkiveringFeilet)
				loggAntallAvHendelsetype(HendelseType.ArkiveringFeilet)
			}

			logger.info("Kafka: Ferdig behandlet mottatt melding med key $messageKey")
		} catch (ex: ResourceNotFoundException) {
			logger.warn("Kafka: fant ikke søknad med key $messageKey i database.")
		} catch (ex: Exception) {
			logger.warn("Kafka exception: ${ex.message}", ex)
			return false
		}
		return true
	}

	private fun loggAntallAvHendelsetype(hendelseType: HendelseType) {
		logger.info("Antall søknader med hendelsetype $hendelseType = ${repo.findNumberOfEventsByType(hendelseType)}")
	}

	private fun publiserInnsendtSoknadOppdatering(soknad: SoknadDbData, message: String) {
		val journalpostId = message.substringAfter("journalpostId=")
		val innsendtOppdatering =
			when (soknad.ettersendingsid == null || soknad.innsendingsid == soknad.ettersendingsid) {
				true -> SoknadEventBuilder.oppdatert {
						this.soknadsId = soknad.innsendingsid
						// this.innsendingsId = soknad.innsendingsid
						this.journalpostId = journalpostId
						this.produsent = SoknadEvent.Dto.Produsent(cluster = publisherConfig.cluster, namespace = publisherConfig.team, appnavn = publisherConfig.application)
					}
				else -> SoknadEventBuilder.vedleggOppdatert {
						this.soknadsId = soknad.ettersendingsid
						//this.eventId = soknad.innsendingsId
						this.journalpostId = journalpostId

						this.produsent = SoknadEvent.Dto.Produsent(cluster = publisherConfig.cluster, namespace = publisherConfig.team, appnavn = publisherConfig.application)
				}
			}
		kafkaPublisher.publishToKvitteringsSide(soknad.innsendingsid, innsendtOppdatering)
	}
}

