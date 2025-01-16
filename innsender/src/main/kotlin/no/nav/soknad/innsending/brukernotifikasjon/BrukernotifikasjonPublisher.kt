package no.nav.soknad.innsending.brukernotifikasjon

import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.NotificationInfo
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.arkivering.soknadsmottaker.model.Varsel
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.ukjentEttersendingsId
import no.nav.soknad.innsending.util.models.erEttersending
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
@EnableConfigurationProperties(BrukerNotifikasjonConfig::class)
class BrukernotifikasjonPublisher(
	private val notifikasjonConfig: BrukerNotifikasjonConfig,
	private val sendTilKafkaPublisher: PublisherInterface
) {
	private val logger = LoggerFactory.getLogger(BrukernotifikasjonPublisher::class.java)

	val tittelPrefixEttersendelse = mapOf(
		"no" to "Ettersend manglende vedlegg til: ",
		"nn" to "Ettersend manglande vedlegg til: ",
		"en" to "Submit missing documentation to: "
	)
	val tittelPrefixNySoknad = mapOf(
		"no" to "",
		"nn" to "",
		"en" to ""
	)

	private fun computeGroupId(dokumentSoknad: DokumentSoknadDto) =
		dokumentSoknad.ettersendingsId.takeIf { it != null && it != ukjentEttersendingsId }
			?: dokumentSoknad.innsendingsId

	fun soknadStatusChange(dokumentSoknad: DokumentSoknadDto): Boolean {
		logger.debug("${dokumentSoknad.innsendingsId}: Skal publisere Brukernotifikasjon")

		if (notifikasjonConfig.publisereEndringer) {
			val groupId = computeGroupId(dokumentSoknad)

			logger.info(
				"${dokumentSoknad.innsendingsId}: Publiser statusendring på søknad" +
					": innsendingsId=${dokumentSoknad.innsendingsId}, status=${dokumentSoknad.status}, groupId=$groupId" +
					", isDokumentInnsending=true, isEttersendelse=${dokumentSoknad.erEttersending}" +
					", tema=${dokumentSoknad.tema}"
			)

			try {
				when (dokumentSoknad.status) {
					SoknadsStatusDto.Opprettet -> handleNewApplication(dokumentSoknad, groupId!!)
					SoknadsStatusDto.Innsendt -> handleSentInApplication(dokumentSoknad, groupId!!)
					SoknadsStatusDto.SlettetAvBruker, SoknadsStatusDto.AutomatiskSlettet -> handleDeletedApplication(
						dokumentSoknad,
						groupId!!
					)
					// Ingen brukernotifikasjon for utfylt søknad
					SoknadsStatusDto.Utfylt -> {}
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
		val ettersending = dokumentSoknad.erEttersending
		val lenke = createLink(dokumentSoknad)
		val notificationInfo = createNotificationInfo(dokumentSoknad, ettersending, lenke)

		val soknadRef = SoknadRef(
			dokumentSoknad.innsendingsId!!,
			ettersending,
			groupId,
			dokumentSoknad.brukerId,
			dokumentSoknad.opprettetDato,
			erSystemGenerert = dokumentSoknad.erSystemGenerert == true,
		)

		try {
			sendTilKafkaPublisher.opprettBrukernotifikasjon(AddNotification(soknadRef, notificationInfo))
			logger.info("${dokumentSoknad.innsendingsId}: Har sendt melding om ny brukernotifikasjon med lenke $lenke")
		} catch (e: Exception) {
			logger.error("${dokumentSoknad.innsendingsId}: Sending av melding om ny brukernotifikasjon feilet", e)
		}
	}

	private fun createNotificationInfo(
		dokumentSoknad: DokumentSoknadDto,
		ettersending: Boolean,
		lenke: String
	): NotificationInfo {
		val tittel = tittelPrefixGittSprak(ettersending, dokumentSoknad.spraak ?: "no") + dokumentSoknad.tittel
		val eksternVarslingList = if (ettersending) mutableListOf(Varsel(Varsel.Kanal.sms)) else mutableListOf()

		val soknadLevetid = dokumentSoknad.mellomlagringDager ?: Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD.toInt()
		val utsettSendingTil = if (dokumentSoknad.erSystemGenerert == true && dokumentSoknad.erNavInitiert != true)
			LocalDateTime.now().plusDays(Constants.DEFAULT_UTSETT_SENDING_VED_SYSTEMGENERERT_DAGER)
				.withHour(9).withMinute(0).withSecond(0).atOffset(ZoneOffset.UTC) else null
		return NotificationInfo(tittel, lenke, soknadLevetid, eksternVarslingList, utsettSendingTil = utsettSendingTil)
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
		val soknadRef = SoknadRef(
			dokumentSoknad.innsendingsId!!,
			ettersending,
			groupId,
			dokumentSoknad.brukerId,
			dokumentSoknad.opprettetDato
		)

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

	private fun createLink(dokumentSoknad: DokumentSoknadDto): String {
		if (dokumentSoknad.soknadstype == SoknadType.soknad && dokumentSoknad.visningsType == VisningsType.fyllUt) {
			val baseUrl = "${notifikasjonConfig.fyllutUrl}/${dokumentSoknad.skjemaPath}/oppsummering"

			val uriBuilder = UriComponentsBuilder.fromUriString(baseUrl)
			uriBuilder.queryParam("sub", "digital")
			uriBuilder.queryParam("innsendingsId", dokumentSoknad.innsendingsId)

			return uriBuilder.toUriString()
		}

		return "${notifikasjonConfig.sendinnUrl}/${dokumentSoknad.innsendingsId}"
	}
}
