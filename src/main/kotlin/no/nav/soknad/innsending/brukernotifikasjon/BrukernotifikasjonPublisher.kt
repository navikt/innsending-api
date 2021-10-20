package no.nav.soknad.innsending.brukernotifikasjon

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.brukernotifikasjon.schemas.builders.BeskjedBuilder
import no.nav.brukernotifikasjon.schemas.builders.DoneBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelBuilder
import no.nav.brukernotifikasjon.schemas.builders.OppgaveBuilder
import no.nav.soknad.innsending.brukernotifikasjon.kafka.KafkaPublisher
import no.nav.soknad.innsending.config.AppConfiguration
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URL
import java.time.LocalDateTime
import java.util.stream.Collectors

@Service
class BrukernotifikasjonPublisher(appConfiguration: AppConfiguration, private val kafkaPublisher: KafkaPublisher) {

	private val appConfig = appConfiguration.kafkaConfig
	private val securityLevel = 4 // Forutsetter at brukere har logget seg på f.eks. bankId slik at nivå 4 er oppnådd
	private val soknadLevetid = 56L // Dager
	val tittelPrefixEttersendelse = "Du har sagt du skal ettersende vedlegg til "
	val tittelPrefixNySoknad = "Du har påbegynt en søknad om "
	val linkDagpenger = "/soknaddagpenger-innsending/soknad/"
	val linkDagpengerEttersending = "/soknaddagpenger-innsending/startettersending/"
	val linkSoknader = "/soknadinnsending/soknad/"
	val linkSoknaderEttersending = "/soknadinnsending/startettersending/"
	val linkDokumentinnsending = "/dokumentinnsending/oversikt/"

	private val logger = LoggerFactory.getLogger(BrukernotifikasjonPublisher::class.java)
	private val OBJECT_MAPPER = ObjectMapper()

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
				SoknadsStatus.Slettet_av_bruker, SoknadsStatus.Automatisk_slettet -> handleDeletedApplication(dokumentSoknad, groupId!!)
				else -> logger.warn("Ingen publiseringshåndtering for henvendelsesstatus = $dokumentSoknad.status")
			}
		}
		return true
	}


	private fun handleNewApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Ny søknad opprettet publiser data slik at søker kan plukke den opp fra Ditt Nav på et senere tidspunkt i tilfelle han/hun ikke ferdigstiller og sender inn
		val key = createKey(dokumentSoknad.innsendingsId!!)
		logger.info("$key: Varsel om ny $dokumentSoknad.innsendingsId skal publiseres")
		val beskjed = newApplication(dokumentSoknad.innsendingsId, groupId, tittelPrefixNySoknad+dokumentSoknad.tittel
			, true, dokumentSoknad.brukerId, dokumentSoknad.tema, dokumentSoknad.ettersendingsId != null, dokumentSoknad.endretDato!!)

		kafkaPublisher.putApplicationMessageOnTopic(key, beskjed)
		logger.info("$key: Varsel om ny $dokumentSoknad.innsendingsId er publisert")

	}

	private fun handleSentInApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Søknad innsendt, fjern beskjed, og opprett eventuelle oppgaver for hvert vedlegg som skal ettersendes
		val key = createKey(dokumentSoknad.innsendingsId!!)

		logger.info("$key: Søknad med behandlingsId=$dokumentSoknad.innsendingsId er sendt inn, publiser done hendelse til Ditt NAV")
		val done = finishedApplication(dokumentSoknad.brukerId, groupId, dokumentSoknad.endretDato!!)
		kafkaPublisher.putApplicationDoneOnTopic(key, done)
		logger.info("$key: Søknad med behandlingsId=$dokumentSoknad.innsendingsId er sendt inn, publisert done hendelse til Ditt NAV")

		val vedlegg = getDocumentsToBeSentInLater(dokumentSoknad.vedleggsListe)
		// Hvis det er ett eller flere dokumenter som skal ettersendes, lag en ettersendelsesoppgave
		if (dokumentSoknad.ettersendingsId != null && !vedlegg.isEmpty()) {
			publishNewAttachmentTask( dokumentSoknad.innsendingsId, groupId, tittelPrefixEttersendelse + dokumentSoknad.tittel,
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
		logger.info("$key: Varsel om fjerning av $dokumentSoknad.innsendingsId skal publiseres")
		val done = finishedApplication(dokumentSoknad.brukerId, groupId, dokumentSoknad.endretDato!!)

		kafkaPublisher.putApplicationDoneOnTopic(key, done)
		logger.info("$key: Varsel om fjerning av $dokumentSoknad.innsendingsId er publisert")

	}

	private fun createKey(innsendingsId: String): Nokkel {
		return NokkelBuilder()
			.withEventId(innsendingsId)
			.withSystembruker(appConfig.username)
			.build()
	}


	private fun getDocumentsToBeSentInLater(vedlegg: List<VedleggDto>): List<VedleggDto> {
		return vedlegg.stream()
			.filter { f -> !f.erHoveddokument }
			.filter { v -> v.opplastingsStatus == OpplastingsStatus.SEND_SENERE }
			.collect(Collectors.toList())
	}


	private fun publishNewAttachmentTask(innsendingsId: String, groupId: String, title: String, dokumentInnsending: Boolean
																			 , personId: String, tema: String, hendelsestidspunkt: LocalDateTime
	) {
		val key = createKey(innsendingsId + "-ettersending")
		logger.info("$key: Varsel om ettersendelsesoppgave til $innsendingsId skal publiseres")
		val oppgave = newTask(innsendingsId, groupId, title, dokumentInnsending, personId, tema, true, hendelsestidspunkt)

		kafkaPublisher.putApplicationTaskOnTopic(key, oppgave)
		logger.info("$key: Varsel om Ettersendelsesoppgave til $innsendingsId er publisert")

	}

	private fun publishRemoveAttachmentTask(innsendingsId: String, groupId: String, title: String, dokumentInnsending: Boolean
																					, personId: String, tema: String, hendelsestidspunkt: LocalDateTime
	) {
		val key = createKey(innsendingsId+ "-ettersending")
		logger.info("$key: Varsel om fjerning av ettersendelsesoppgave til $innsendingsId skal publiseres")
		val done = finishedApplication(personId, groupId, hendelsestidspunkt)

		kafkaPublisher.putApplicationDoneOnTopic(key, done)
		logger.info("$key: Varsel om fjerning av ettersendelsesoppgave til $innsendingsId er publisert")

	}

	private fun newTask(innsendingsId: String, groupId: String, title: String, dokumentInnsending: Boolean
											, personId: String, tema: String, ettersending: Boolean, hendelsestidspunkt: LocalDateTime
	): Oppgave {
		return OppgaveBuilder()
			.withFodselsnummer(personId)
			.withGrupperingsId(groupId)
			.withTekst(title)
			.withLink(URL(createLink(innsendingsId, dokumentInnsending, tema, ettersending)))
			.withSikkerhetsnivaa(securityLevel)
			.withTidspunkt(hendelsestidspunkt)
			.build()
	}


	private fun newApplication(innsendingsId: String, groupId: String, title: String, dokumentInnsending: Boolean
														 , personId: String, tema: String, ettersending: Boolean, hendelsestidspunkt: LocalDateTime
	): Beskjed {
		return BeskjedBuilder()
			.withFodselsnummer(personId)
			.withGrupperingsId(groupId)
			.withTekst(title)
			.withLink(URL(createLink(innsendingsId, dokumentInnsending, tema, ettersending)))
			.withSikkerhetsnivaa(securityLevel)
			.withTidspunkt(hendelsestidspunkt)
			.withSynligFremTil(LocalDateTime.now().plusDays(soknadLevetid))
			.build()
	}

	private fun finishedApplication(personId: String, groupId: String, hendelsestidspunkt: LocalDateTime): Done {
		return DoneBuilder()
			.withFodselsnummer(personId)
			.withGrupperingsId(groupId)
			.withTidspunkt(hendelsestidspunkt)
			.build()
	}

	private fun createLink(innsendingsId: String, dokumentInnsending: Boolean, tema: String, ettersending: Boolean): String {
		// Eksempler:
		// Ettersendingslink: https://tjenester-q1.nav.no/soknadinnsending/startettersending/10010WQEF
		// Eksempel https://www.nav.no/soknader/nb/person/stonader-ved-dodsfall/barn-som-har-mistet-en-eller-begge-foreldrene/NAV%2018-01.05/ettersendelse/dokumentinnsending
		// Fortsett dagpengesøknadlink: https://tjenester.nav.no/soknaddagpenger-innsending/soknad/10014Qi1G
		if (dokumentInnsending) {
			return appConfig.tjenesteUrl + linkDokumentinnsending + innsendingsId
		} else {
			if ("DAG".equals(tema, true)) {
				if (ettersending) {
					return appConfig.tjenesteUrl + linkDagpengerEttersending + innsendingsId
				}
				return appConfig.tjenesteUrl + linkDagpenger + innsendingsId
			} else {
				// Merk at det ikke sendes inn søknader relatert til BID via henvendelse, kun dokumentinnsending
				if (ettersending) {
					return appConfig.tjenesteUrl + linkSoknaderEttersending + innsendingsId
				}
				return appConfig.tjenesteUrl + linkSoknader + innsendingsId
			}
		}

	}

}
