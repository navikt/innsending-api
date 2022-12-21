package no.nav.soknad.innsending.brukernotifikasjon

import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.NotificationInfo
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.service.ukjentEttersendingsId
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service

@Service
@EnableConfigurationProperties(BrukerNotifikasjonConfig::class)
class BrukernotifikasjonPublisher(
	private val notifikasjonConfig: BrukerNotifikasjonConfig,
	private val sendTilKafkaPublisher: PublisherInterface
) {
	private val logger = LoggerFactory.getLogger(BrukernotifikasjonPublisher::class.java)

	private val soknadLevetid = Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD.toInt() // Dager

	val tittelPrefixEttersendelse = mapOf("no" to "Ettersend manglende vedlegg til: ",
		"nn" to "Ettersend manglande vedlegg til: ",
		"en" to "Submit missing documentation to: "
	)
	val tittelPrefixNySoknad = mapOf("no" to "",
		"nn" to "",
		"en" to ""
	)
	val linkDokumentinnsending = notifikasjonConfig.gjenopptaSoknadsArbeid
	val linkDokumentinnsendingEttersending = notifikasjonConfig.ettersendePaSoknad

	fun soknadStatusChange(dokumentSoknad: DokumentSoknadDto): Boolean {
		logger.debug("${dokumentSoknad.innsendingsId}: Skal publisere Brukernotifikasjon")

		if (notifikasjonConfig.publisereEndringer) {
			val groupId = if (dokumentSoknad.ettersendingsId != null && dokumentSoknad.ettersendingsId != ukjentEttersendingsId)
					dokumentSoknad.ettersendingsId
				else
					dokumentSoknad.innsendingsId

			logger.info(
				"${dokumentSoknad.innsendingsId}: Publiser statusendring på søknad" +
					": innsendingsId=${dokumentSoknad.innsendingsId}, status=${dokumentSoknad.status}, groupId=$groupId" +
					", isDokumentInnsending=true, isEttersendelse=${erEttersending(dokumentSoknad)}" +
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
				logger.info("${dokumentSoknad.innsendingsId}: Publisering av brukernotifikasjon feilet", e)
				return false
			}
		}
		return true
	}


	private fun handleNewApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Ny søknad opprettet publiser data slik at søker kan plukke den opp fra Ditt Nav på et senere tidspunkt
		// i tilfelle han/hun ikke ferdigstiller og sender inn
		val ettersending = erEttersending(dokumentSoknad)
		val tittel = tittelPrefixGittSprak(ettersending, dokumentSoknad.spraak ?: "no") + dokumentSoknad.tittel
		val lenke = createLink(dokumentSoknad.innsendingsId!!, false)

		val notificationInfo = NotificationInfo(tittel, lenke , soknadLevetid , emptyList())
		val soknadRef = SoknadRef(dokumentSoknad.innsendingsId!!, ettersending, groupId, dokumentSoknad.brukerId, dokumentSoknad.opprettetDato)

		try {
			sendTilKafkaPublisher.opprettBrukernotifikasjon(AddNotification(soknadRef, notificationInfo))
			logger.info("${dokumentSoknad.innsendingsId}: Har sendt melding om ny brukernotifikasjon med lenke $lenke")
		} catch (e: Exception) {
			logger.error("${dokumentSoknad.innsendingsId}: Sending av melding om ny brukernotifikasjon feilet", e)
		}
	}

	private fun tittelPrefixGittSprak(ettersendelse: Boolean, sprak: String): String {
		return if (ettersendelse)
			tittelPrefixEttersendelse[sprak] ?: tittelPrefixEttersendelse["no"]!!
		else tittelPrefixNySoknad[sprak] ?: tittelPrefixNySoknad["no"]!!
	}

	private fun erEttersending(dokumentSoknad: DokumentSoknadDto): Boolean =
		(dokumentSoknad.ettersendingsId != null) || (dokumentSoknad.visningsType == VisningsType.ettersending)

	private fun handleSentInApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Søknad innsendt, fjern beskjed, og opprett eventuelle oppgaver for hvert vedlegg som skal ettersendes
		publishDoneEvent(dokumentSoknad, groupId)
	}

	private fun publishDoneEvent(dokumentSoknad: DokumentSoknadDto, groupId: String) {
			val ettersending = erEttersending(dokumentSoknad)
			val soknadRef = SoknadRef(dokumentSoknad.innsendingsId!!, ettersending, groupId, dokumentSoknad.brukerId, dokumentSoknad.opprettetDato)

			try {
				sendTilKafkaPublisher.avsluttBrukernotifikasjon(soknadRef)
				logger.info("${dokumentSoknad.innsendingsId}: Har sendt melding om avslutning av brukernotifikasjon")
			} catch (e: Exception) {
				logger.error("${dokumentSoknad.innsendingsId}: Sending av melding om avslutning av brukernotifikasjon feilet", e)
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
