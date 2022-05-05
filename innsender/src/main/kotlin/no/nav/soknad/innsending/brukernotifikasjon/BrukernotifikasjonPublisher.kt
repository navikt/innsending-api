package no.nav.soknad.innsending.brukernotifikasjon

import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.NotificationInfo
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.arkivering.soknadsmottaker.model.Varsel
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service

@Service
@EnableConfigurationProperties(BrukerNotifikasjonConfig::class)
class BrukernotifikasjonPublisher(
	private val notifikasjonConfig: BrukerNotifikasjonConfig,
	private val sendTilKafkaPublisher: PublisherInterface
) {

	private val logger = LoggerFactory.getLogger(BrukernotifikasjonPublisher::class.java)

	private val soknadLevetid = 56 // Dager
	private val ettersendingsFrist = 56 // Dager
	val tittelPrefixEttersendelse = "Du har sagt du skal ettersende vedlegg til "
	val tittelPrefixNySoknad = "Du har påbegynt en søknad om "
	val linkDokumentinnsending = notifikasjonConfig.gjenopptaSoknadsArbeid
	val linkDokumentinnsendingEttersending = notifikasjonConfig.ettersendePaSoknad
	val eksternVarsling = false
	val epostTittelNySoknad = "Ny soknad opprettet"
	val epostTittelNyEttersending = "Ny soknad for ettersending av dokumentasjon opprettet"
	val epostTittelEttersending = "Husk å ettersende manglende dokumentasjon"

	fun soknadStatusChange(dokumentSoknad: DokumentSoknadDto): Boolean {

		logger.debug("Skal publisere event relatert til ${dokumentSoknad.innsendingsId}")

		if (notifikasjonConfig.publisereEndringer) {
			val groupId = dokumentSoknad.ettersendingsId ?: dokumentSoknad.innsendingsId

			logger.info(
				"Publiser statusendring på søknad" +
					": innsendingsId=${dokumentSoknad.innsendingsId}, status=${dokumentSoknad.status}, groupId=$groupId" +
					", isDokumentInnsending=true, isEttersendelse=${dokumentSoknad.ettersendingsId != null}" +
					", tema=${dokumentSoknad.tema}"
			)

			try {
				when (dokumentSoknad.status) {
					SoknadsStatusDto.opprettet -> handleNewApplication(dokumentSoknad, groupId!!)
					SoknadsStatusDto.innsendt -> handleSentInApplication(dokumentSoknad, groupId!!)
					SoknadsStatusDto.slettetAvBruker, SoknadsStatusDto.automatiskSlettet -> handleDeletedApplication(
						dokumentSoknad,
						groupId!!
					)
				}
			} catch (e: Exception) {
				logger.info("Publisering av brukernotifikasjon feilet med ${e.message}")
				return false
			}
		}
		return true
	}


	private fun handleNewApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Ny søknad opprettet publiser data slik at søker kan plukke den opp fra Ditt Nav på et senere tidspunkt
		// i tilfelle han/hun ikke ferdigstiller og sender inn
		val ettersending = erEttersending(dokumentSoknad)
		val tittel = (if (ettersending) tittelPrefixEttersendelse else tittelPrefixNySoknad) + dokumentSoknad.tittel
		val lenke = createLink(dokumentSoknad.innsendingsId!!, false)

		val notificationInfo = NotificationInfo(tittel, lenke , if (ettersending) ettersendingsFrist else  soknadLevetid , emptyList())
		val soknadRef = SoknadRef(dokumentSoknad.innsendingsId!!, ettersending, groupId, dokumentSoknad.brukerId, dokumentSoknad.opprettetDato)

		logger.info("${dokumentSoknad.innsendingsId}: Sende melding om ny brukernotifikasjon med lenke $lenke")
		try {
			sendTilKafkaPublisher.opprettBrukernotifikasjon(AddNotification(soknadRef, notificationInfo))
		} catch (ex: Exception) {
			logger.error("${dokumentSoknad.innsendingsId}: Sending av melding om ny brukernotifikasjon feilet: ${ex.message}")
		}
	}

	private fun erEttersending(dokumentSoknad: DokumentSoknadDto): Boolean = dokumentSoknad.ettersendingsId != null

	private fun handleSentInApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Søknad innsendt, fjern beskjed, og opprett eventuelle oppgaver for hvert vedlegg som skal ettersendes
		publishDoneEvent(dokumentSoknad, groupId)

	}

	private fun publishDoneEvent(dokumentSoknad: DokumentSoknadDto, groupId: String) {
			val ettersending = erEttersending(dokumentSoknad)
			val soknadRef = SoknadRef(dokumentSoknad.innsendingsId!!, ettersending, groupId, dokumentSoknad.brukerId, dokumentSoknad.opprettetDato)

			logger.info("${dokumentSoknad.innsendingsId}: Sende melding om avslutning av brukernotifikasjon")
			try {
				sendTilKafkaPublisher.avsluttBrukernotifikasjon(soknadRef)
			} catch (ex: Exception) {
				logger.error("${dokumentSoknad.innsendingsId}: Sending av melding om avslutning av brukernotifikasjon feilet med: ${ex.message}")
			}
	}

	private fun handleDeletedApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Søknad slettet, fjern beskjed
		publishDoneEvent(dokumentSoknad, groupId)
	}

	private fun createLink(innsendingsId: String, opprettEttersendingsLink: Boolean): String {
		// Eksempler:
		// Fortsett senere: https://tjenester-q1.nav.no/dokumentinnsending/oversikt/10014Qi1G For å gjenoppta påbegynt søknad
		// Ettersendingslink: https://tjenester-q1.nav.no/dokumentinnsending/ettersendelse/10010WQEF For å opprette søknad for å ettersende vedlegg på tidligere innsendt søknad (10010WQEF)
		return if (opprettEttersendingsLink)
			notifikasjonConfig.tjenesteUrl + linkDokumentinnsendingEttersending + innsendingsId  // Nytt endepunkt
		else
			notifikasjonConfig.tjenesteUrl + linkDokumentinnsending + innsendingsId
	}

}
