package no.nav.soknad.innsending.brukernotifikasjon

import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.NotificationInfo
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.arkivering.soknadsmottaker.model.Varsel
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.location.UrlHandler
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.mapping.toOffsetDateTime
import no.nav.soknad.innsending.util.soknaddbdata.getSkjemaPath
import no.nav.soknad.innsending.util.soknaddbdata.isEttersending
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

@Service
@EnableConfigurationProperties(BrukerNotifikasjonConfig::class)
class BrukernotifikasjonPublisher(
	private val notifikasjonConfig: BrukerNotifikasjonConfig,
	private val sendTilKafkaPublisher: PublisherInterface,
	private val urlHandler: UrlHandler,
	private val soknadRepository: SoknadRepository,
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

	@Retryable(
		maxAttempts = 5,
		backoff = Backoff(
			delay = 5000,
			multiplier = 2.0
		)
	)
	fun createNotification(inputSoknad: SoknadDbData, opts: NotificationOptions = NotificationOptions()): Boolean {
		if (!notifikasjonConfig.publisereEndringer) {
			return true
		}
		val soknad = soknadRepository.findByInnsendingsid(inputSoknad.innsendingsid)
		if (soknad == null) {
			logger.info("Fant ikke søknad med innsendingsId ${inputSoknad.innsendingsid} i databasen, kan ikke publisere brukernotifikasjon")
			return false
		}
		if (soknad.brukerid.isNullOrEmpty()) {
			logger.info("${soknad.innsendingsid}: Brukerid mangler, kan ikke publisere brukernotifikasjon")
			return false
		}
		if (soknad.status != SoknadsStatus.Opprettet && soknad.status != SoknadsStatus.Utfylt ) {
			logger.info("${soknad.innsendingsid}: Status er ikke Opprettet eller Utfylt, skal ikke publisere brukernotifikasjon")
			return false
		}

		logger.debug("${soknad.innsendingsid}: Skal publisere Brukernotifikasjon")
		try {
			val tittel = if (soknad.isEttersending()) "${getEttersendingPrefix(soknad)}${soknad.tittel}" else soknad.tittel
			val lenke = createLink(soknad, opts.envQualifier)
			val info = NotificationInfo(
				tittel,
				lenke,
				daysUntil(soknad.skalslettesdato) + 1,
				if (soknad.isEttersending()) listOf(Varsel(Varsel.Kanal.sms)) else emptyList(),
				if (opts.erSystemGenerert && !opts.erNavInitiert) getUtsattTidspunkt() else null
			)
			val groupId = soknad.ettersendingsid ?: soknad.innsendingsid
			val soknadRef = SoknadRef(
				soknad.innsendingsid,
				soknad.isEttersending(),
				groupId,
				soknad.brukerid!!,
				soknad.opprettetdato.toOffsetDateTime(),
				opts.erSystemGenerert
			)
			sendTilKafkaPublisher.opprettBrukernotifikasjon(AddNotification(soknadRef, info))
			logger.info("${soknad.innsendingsid}: Har sendt melding om ny brukernotifikasjon med lenke $lenke")
			return true
		} catch (e: Exception) {
			logger.error("${soknad.innsendingsid}: Publisering av brukernotifikasjon feilet", e)
			throw e
		}
	}

	@Retryable(
		maxAttempts = 5,
		backoff = Backoff(
			delay = 5000,
			multiplier = 2.0
		)
	)
	fun closeNotification(soknad: SoknadDbData): Boolean {
		if (!notifikasjonConfig.publisereEndringer) {
			return true
		}
		if (soknad.brukerid.isNullOrEmpty()) {
			logger.info("${soknad.innsendingsid}: Brukerid mangler, kan ikke avslutte brukernotifikasjon")
			return false
		}
		logger.debug("${soknad.innsendingsid}: Skal avslutte Brukernotifikasjon")
		try {
			val soknadRef = SoknadRef(
				soknad.innsendingsid,
				soknad.isEttersending(),
				soknad.ettersendingsid ?: soknad.innsendingsid,
				soknad.brukerid!!,
				soknad.opprettetdato.toOffsetDateTime(),
			)
			sendTilKafkaPublisher.avsluttBrukernotifikasjon(soknadRef)
			logger.info("${soknad.innsendingsid}: Har sendt melding om avslutning av brukernotifikasjon")
			return true
		} catch (e: Exception) {
			logger.error("${soknad.innsendingsid}: Sending av melding om avslutning av brukernotifikasjon feilet", e)
			throw e
		}
	}

	private fun createLink(soknad: SoknadDbData, envQualifier: EnvQualifier?): String {
		if (soknad.visningstype == VisningsType.fyllUt && !soknad.isEttersending()) {
			val baseUrl = "${urlHandler.getFyllutUrl(envQualifier)}/${soknad.getSkjemaPath()}/oppsummering"

			return UriComponentsBuilder.fromUriString(baseUrl)
				.queryParam("sub", "digital")
				.queryParam("innsendingsId", soknad.innsendingsid)
				.toUriString()
		}

		return "${urlHandler.getSendInnUrl(envQualifier)}/${soknad.innsendingsid}"
	}

	fun getUtsattTidspunkt(): OffsetDateTime =
		LocalDateTime.now().plusDays(Constants.DEFAULT_UTSETT_SENDING_VED_SYSTEMGENERERT_DAGER)
			.withHour(9).withMinute(0).withSecond(0).atOffset(ZoneOffset.UTC)

	fun getEttersendingPrefix(soknad: SoknadDbData): String =
		tittelPrefixEttersendelse[soknad.spraak] ?: tittelPrefixEttersendelse["no"]!!

	fun daysUntil(dateTime: OffsetDateTime) =
		ChronoUnit.DAYS.between(LocalDateTime.now(), dateTime.toLocalDateTime()).toInt() + 1
}

data class NotificationOptions(
	val erSystemGenerert: Boolean = false,
	val erNavInitiert: Boolean = false,
	val envQualifier: EnvQualifier? = null,
)
