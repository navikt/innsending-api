package no.nav.soknad.innsending.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.soknad.innsending.brukernotifikasjon.kafka.KafkaPublisherInterface
import no.nav.soknad.innsending.config.AppConfiguration
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
class BrukernotifikasjonPublisher(private val appConfiguration: AppConfiguration, private val kafkaPublisher: KafkaPublisherInterface) {

	private val appConfig = appConfiguration.kafkaConfig
	private val securityLevel = 4 // Forutsetter at brukere har logget seg på f.eks. bankId slik at nivå 4 er oppnådd
	private val soknadLevetid = 56L // Dager
	val tittelPrefixEttersendelse = "Du har sagt du skal ettersende vedlegg til "
	val tittelPrefixNySoknad = "Du har påbegynt en søknad om "
	val linkDokumentinnsending = appConfig.gjenopptaSoknadsArbeid
	val linkDokumentinnsendingEttersending = appConfig.ettersendePaSoknad
	val eksternVarsling = true

	private val logger = LoggerFactory.getLogger(BrukernotifikasjonPublisher::class.java)

	fun soknadStatusChange(dokumentSoknad: DokumentSoknadDto): Boolean {

		logger.debug("Skal publisere event relatert til ${dokumentSoknad.innsendingsId}")

		if (appConfig.publisereEndringer) {
			val groupId = dokumentSoknad.ettersendingsId ?: dokumentSoknad.innsendingsId

			logger.info("Publiser statusendring på søknad:" +
				" innsendingsId=${dokumentSoknad.innsendingsId}, status=${dokumentSoknad.status}, groupId=$groupId" +
				", isDokumentInnsending=true," + "isEttersendelse=${dokumentSoknad.ettersendingsId != null}, tema=${dokumentSoknad.tema}" )

			when (dokumentSoknad.status) {
				SoknadsStatus.Opprettet -> handleNewApplication(dokumentSoknad, groupId!!)
				SoknadsStatus.Innsendt -> handleSentInApplication(dokumentSoknad, groupId!!)
				SoknadsStatus.SlettetAvBruker, SoknadsStatus.AutomatiskSlettet -> handleDeletedApplication(dokumentSoknad, groupId!!)
			}
		}
		return true
	}


	private fun handleNewApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Ny søknad opprettet publiser data slik at søker kan plukke den opp fra Ditt Nav på et senere tidspunkt i tilfelle han/hun ikke ferdigstiller og sender inn
		val key = createKey(dokumentSoknad.innsendingsId!!)
		logger.info("$key: Varsel om ny ${dokumentSoknad.innsendingsId} skal publiseres")
		val beskjed = newApplication(dokumentSoknad.innsendingsId, groupId, tittelPrefixNySoknad+dokumentSoknad.tittel
			, true, dokumentSoknad.brukerId, dokumentSoknad.tema, dokumentSoknad.ettersendingsId != null, dokumentSoknad.endretDato!!)

		kafkaPublisher.putApplicationMessageOnTopic(key, beskjed)
		logger.info("$key: Varsel om ny ${dokumentSoknad.innsendingsId} er publisert")
	}

	private fun handleSentInApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Søknad innsendt, fjern beskjed, og opprett eventuelle oppgaver for hvert vedlegg som skal ettersendes
		val key = createKey(dokumentSoknad.innsendingsId!!)

		logger.info("$key: Søknad med behandlingsId=${dokumentSoknad.innsendingsId} er sendt inn, publiser done hendelse til Ditt NAV")
		val done = finishedApplication(dokumentSoknad.brukerId, groupId, dokumentSoknad.endretDato!!)
		kafkaPublisher.putApplicationDoneOnTopic(key, done)
		logger.info("$key: Søknad med behandlingsId=${dokumentSoknad.innsendingsId} er sendt inn, publisert done hendelse til Ditt NAV")

		val vedlegg = getDocumentsToBeSentInLater(dokumentSoknad.vedleggsListe)
		// Hvis det er ett eller flere dokumenter som skal ettersendes, lag en ettersendelsesoppgave
		if (dokumentSoknad.ettersendingsId == null && vedlegg.isNotEmpty()) {
			publishNewAttachmentTask(dokumentSoknad.innsendingsId, groupId, tittelPrefixEttersendelse + dokumentSoknad.tittel,
				true, dokumentSoknad.brukerId, dokumentSoknad.tema, dokumentSoknad.endretDato)
		} else {
			// Hvis ettersending, og ingen dokumenter som skal sendes inn senere, fjern eventuell ettersendingsoppgave
			if (dokumentSoknad.ettersendingsId != null && vedlegg.isEmpty()) {
				publishRemoveAttachmentTask(
					dokumentSoknad.ettersendingsId,
					groupId,
					tittelPrefixEttersendelse + dokumentSoknad.tittel,
					true,
					dokumentSoknad.brukerId,
					dokumentSoknad.tema,
					dokumentSoknad.endretDato
				)
			}
		}
	}

	private fun handleDeletedApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Søknad slettet, fjern beskjed
		val key = createKey(dokumentSoknad.innsendingsId!!)
		logger.info("$key: Varsel om fjerning av ${dokumentSoknad.innsendingsId} skal publiseres")
		val done = finishedApplication(dokumentSoknad.brukerId, groupId, dokumentSoknad.endretDato!!)

		kafkaPublisher.putApplicationDoneOnTopic(key, done)
		logger.info("$key: Varsel om fjerning av ${dokumentSoknad.innsendingsId} er publisert")
	}

	private fun createKey(innsendingsId: String) = Nokkel(appConfig.username, innsendingsId)


	private fun getDocumentsToBeSentInLater(vedlegg: List<VedleggDto>): List<VedleggDto> {
		return vedlegg
			.filter { f -> !f.erHoveddokument }
			.filter { v -> v.opplastingsStatus == OpplastingsStatus.SEND_SENERE }
	}


	private fun publishNewAttachmentTask(innsendingsId: String, groupId: String, title: String, dokumentInnsending: Boolean
																			 , personId: String, tema: String, hendelsestidspunkt: LocalDateTime
	) {
		val key = createKey("$innsendingsId-ettersending")
		logger.info("$key: Varsel om ettersendelsesoppgave til $innsendingsId skal publiseres")
		val oppgave = newTask(innsendingsId, groupId, title, dokumentInnsending, personId, tema, true, hendelsestidspunkt)

		kafkaPublisher.putApplicationTaskOnTopic(key, oppgave)
		logger.info("$key: Varsel om Ettersendelsesoppgave til $innsendingsId er publisert")
	}

	private fun publishRemoveAttachmentTask(innsendingsId: String, groupId: String, title: String, dokumentInnsending: Boolean
																					, personId: String, tema: String, hendelsestidspunkt: LocalDateTime
	) {
		val key = createKey("$innsendingsId-ettersending")
		logger.info("$key: Varsel om fjerning av ettersendelsesoppgave til $innsendingsId skal publiseres")
		val done = finishedApplication(personId, groupId, hendelsestidspunkt)

		kafkaPublisher.putApplicationDoneOnTopic(key, done)
		logger.info("$key: Varsel om fjerning av ettersendelsesoppgave til $innsendingsId er publisert")
	}

	private fun newTask(innsendingsId: String, groupId: String, title: String, dokumentInnsending: Boolean,
											personId: String, tema: String, ettersending: Boolean, hendelsestidspunkt: LocalDateTime
	) = Oppgave(hendelsestidspunkt.toEpochSecond(ZoneOffset.UTC), personId, groupId, title,
			createLink(innsendingsId, dokumentInnsending, tema, ettersending), securityLevel, eksternVarsling, emptyList())


	private fun newApplication(innsendingsId: String, groupId: String, title: String, dokumentInnsending: Boolean
														 , personId: String, tema: String, ettersending: Boolean, hendelsestidspunkt: LocalDateTime
	) = Beskjed(hendelsestidspunkt.toEpochSecond(ZoneOffset.UTC), LocalDateTime.now().plusDays(soknadLevetid).toEpochSecond(
			ZoneOffset.UTC), personId, groupId, title,
			createLink(innsendingsId, dokumentInnsending, tema, ettersending), securityLevel, eksternVarsling, emptyList())

	private fun finishedApplication(personId: String, groupId: String, hendelsestidspunkt: LocalDateTime) =
		Done(hendelsestidspunkt.toEpochSecond(ZoneOffset.UTC), personId, groupId)

	private fun createLink(innsendingsId: String, dokumentInnsending: Boolean, tema: String, ettersending: Boolean): String {
		// Eksempler:
		// Fortsett senere: https://tjenester-q1.nav.no/dokumentinnsending/oversikt/10014Qi1G For å gjenoppta påbegynt søknad
		// Ettersendingslink: https://tjenester-q1.nav.no/dokumentinnsending/ettersendelse/10010WQEF For å ettersende vedlegg på tidligere innsendt søknad (10010WQEF)
			if (ettersending) {
				return appConfig.tjenesteUrl + linkDokumentinnsendingEttersending + innsendingsId  // Nytt endepunkt
			}
			return appConfig.tjenesteUrl + linkDokumentinnsending + innsendingsId
	}
}
