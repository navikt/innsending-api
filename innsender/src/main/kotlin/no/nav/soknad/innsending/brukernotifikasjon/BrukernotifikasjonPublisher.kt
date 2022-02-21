package no.nav.soknad.innsending.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.soknad.innsending.brukernotifikasjon.kafka.KafkaPublisherInterface
import no.nav.soknad.innsending.config.KafkaConfig
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.repository.SoknadsStatus
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

@Service
@EnableConfigurationProperties(KafkaConfig::class)
class BrukernotifikasjonPublisher(
	private val kafkaConfig: KafkaConfig,
	private val kafkaPublisher: KafkaPublisherInterface
) {

	private val logger = LoggerFactory.getLogger(BrukernotifikasjonPublisher::class.java)

	private val securityLevel = 4 // Forutsetter at brukere har logget seg på f.eks. bankId slik at nivå 4 er oppnådd
	private val soknadLevetid = 56L // Dager
	private val ettersendingsFrist = 14L // Dager
	val tittelPrefixEttersendelse = "Du har sagt du skal ettersende vedlegg til "
	val tittelPrefixNySoknad = "Du har påbegynt en søknad om "
	val epostTittelNySoknad = "Ny soknad opprettet"
	val epostTittelNyEttersending = "Ny soknad for ettersending av dokumentasjon opprettet"
	val epostTittelEttersending = "Husk å ettersende manglende dokumentasjon"
	val linkDokumentinnsending = kafkaConfig.gjenopptaSoknadsArbeid
	val linkDokumentinnsendingEttersending = kafkaConfig.ettersendePaSoknad
	val eksternVarsling = true

	fun soknadStatusChange(dokumentSoknad: DokumentSoknadDto): Boolean {

		logger.debug("Skal publisere event relatert til ${dokumentSoknad.innsendingsId}")

		if (kafkaConfig.publisereEndringer) {
			val groupId = dokumentSoknad.ettersendingsId ?: dokumentSoknad.innsendingsId

			logger.info(
				"Publiser statusendring på søknad" +
					": innsendingsId=${dokumentSoknad.innsendingsId}, status=${dokumentSoknad.status}, groupId=$groupId" +
					", isDokumentInnsending=true, isEttersendelse=${dokumentSoknad.ettersendingsId != null}" +
					", tema=${dokumentSoknad.tema}"
			)

			try {
				when (dokumentSoknad.status) {
					SoknadsStatus.Opprettet -> handleNewApplication(dokumentSoknad, groupId!!)
					SoknadsStatus.Innsendt -> handleSentInApplication(dokumentSoknad, groupId!!)
					SoknadsStatus.SlettetAvBruker, SoknadsStatus.AutomatiskSlettet -> handleDeletedApplication(
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
		val key = createKey(dokumentSoknad.innsendingsId!!)

		if (erEttersending(dokumentSoknad)) {
			publiserNyEttersendingsSoknadOppgave(dokumentSoknad.innsendingsId, groupId,
				tittelPrefixEttersendelse + dokumentSoknad.tittel, dokumentSoknad.brukerId, dokumentSoknad.opprettetDato)
		} else {
			publiserNySoknadBeskjed(dokumentSoknad.innsendingsId, groupId, tittelPrefixNySoknad + dokumentSoknad.tittel,
				dokumentSoknad.brukerId, dokumentSoknad.opprettetDato)
		}
	}

	private fun erEttersending(dokumentSoknad: DokumentSoknadDto): Boolean = dokumentSoknad.ettersendingsId != null

	private fun handleSentInApplication(dokumentSoknad: DokumentSoknadDto, groupId: String) {
		// Søknad innsendt, fjern beskjed, og opprett eventuelle oppgaver for hvert vedlegg som skal ettersendes
		val key = createKey(dokumentSoknad.innsendingsId!!)

		publishDoneEvent(dokumentSoknad, groupId, key)

	}

	private fun publishDoneEvent(dokumentSoknad: DokumentSoknadDto, groupId: String, key: Nokkel) {
		try {
			val done = finishedApplication(dokumentSoknad.brukerId, groupId, dokumentSoknad.endretDato!!)
			kafkaPublisher.putApplicationDoneOnTopic(key, done)

			logger.info("$key: Søknad med behandlingsId=${dokumentSoknad.innsendingsId} er sendt inn, publisert " +
					"done-hendelse til Ditt NAV")
		} catch (e: Exception) {
			logger.error("$key: Failed to pubish Done-event to Ditt NAV for Søknad with " +
					"behandlingsId=${dokumentSoknad.innsendingsId}!", e)
			throw e
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

	private fun createKey(innsendingsId: String) = Nokkel.newBuilder()
		.setEventId(innsendingsId)
		.setSystembruker(kafkaConfig.username)
		.build()

	private fun publiserNyEttersendingsSoknadOppgave(
		innsendingsId: String, groupId: String, title: String,
		personId: String, hendelsestidspunkt: LocalDateTime
	) {
		val key = createKey(innsendingsId)
		logger.info("$key: Varsel om ettersendingssøknad til $innsendingsId skal publiseres")
		val oppgave = newApplicationTask(innsendingsId, groupId, title, personId, hendelsestidspunkt)

		kafkaPublisher.putApplicationTaskOnTopic(key, oppgave)
		logger.info("$key: Varsel om ettersendingssøknad til $innsendingsId er publisert")
	}

	private fun publiserNySoknadBeskjed(
		innsendingsId: String, groupId: String, title: String,
		personId: String, hendelsestidspunkt: LocalDateTime
	) {
		val key = createKey(innsendingsId)
		logger.info("$key: Beskjed om ny søknad $innsendingsId skal publiseres")
		val beskjed = newApplicationMessage(
			innsendingsId, groupId, title, personId, false, hendelsestidspunkt
		)

		kafkaPublisher.putApplicationMessageOnTopic(key, beskjed)
		logger.info("$key: Beskjed om ny søknad $innsendingsId er publisert")
	}

	private fun newApplicationTask(
		innsendingsId: String, groupId: String, title: String, personId: String,
		hendelsestidspunkt: LocalDateTime
	): Oppgave {
		val tidspunkt = hendelsestidspunkt.toEpochSecond(ZoneOffset.UTC)
		val dagerTilInnsendingsFrist = ettersendingsFrist - Duration.between( LocalDateTime.now(), hendelsestidspunkt ).toDays()
		val synligFremTil = LocalDateTime.now().plusDays(dagerTilInnsendingsFrist).toEpochSecond(ZoneOffset.UTC)
		val ettersendingsLenke = createLink(innsendingsId, false)

		val epostTekst = "Bruk følgende lenke $ettersendingsLenke for ettersending av manglende dokumentasjon\n"+
			"Vær oppmerksom på at du har frist fram til ${synligFremTil} til å ettersende manglende dokumentasjon."

		return Oppgave.newBuilder()
			.setTidspunkt(tidspunkt)
			.setSikkerhetsnivaa(securityLevel)
			.setFodselsnummer(personId)
			.setGrupperingsId(groupId)
			.setSynligFremTil(synligFremTil)
			.setTekst(title)
			.setLink(createLink(innsendingsId, false))
			.setEksternVarsling(eksternVarsling)
			.setPrefererteKanaler(listOf(PreferertKanal.EPOST.name))
			.setEpostVarslingstittel(epostTittelEttersending)
			.setEpostVarslingstekst(epostTekst)
			.build()

	}

	private fun newApplicationMessage(
		innsendingsId: String, groupId: String, title: String, personId: String,
		ettersending: Boolean, hendelsestidspunkt: LocalDateTime
	): Beskjed {

		val tidspunkt = hendelsestidspunkt.toEpochSecond(ZoneOffset.UTC)

		val dagerTilInnsendingsFrist = soknadLevetid - Duration.between( LocalDateTime.now(), hendelsestidspunkt ).toDays()
		val synligFremTil = LocalDateTime.now().plusDays(dagerTilInnsendingsFrist).toEpochSecond(ZoneOffset.UTC)

		val epostTekstNySoknad = "Hvis du ikke får gjort deg ferdig med søknaden og sendt den inn til NAV, kan du senere logge inn på NAV og finne lenke for å fortsette arbeidet med søknaden\n" +
			"Vær oppmerksom på at hvis du ikke sender inn søknaden vil den automatisk bli slettet etter ${soknadLevetid} dager"

		return Beskjed.newBuilder()
			.setTidspunkt(tidspunkt)
			.setSikkerhetsnivaa(securityLevel)
			.setFodselsnummer(personId)
			.setGrupperingsId(groupId)
			.setSynligFremTil(synligFremTil)
			.setTekst(title)
			.setLink(createLink(innsendingsId, false))
			.setEksternVarsling(eksternVarsling)
			.setPrefererteKanaler(listOf(PreferertKanal.EPOST.name))
			.setEpostVarslingstittel(epostTittelNySoknad)
			.setEpostVarslingstekst(epostTekstNySoknad)
			.build()

	}

	private fun finishedApplication(personId: String, groupId: String, hendelsestidspunkt: LocalDateTime) =
		Done.newBuilder()
			.setTidspunkt(hendelsestidspunkt.toEpochSecond(ZoneOffset.UTC))
			.setFodselsnummer(personId)
			.setGrupperingsId(groupId)
			.build()

	private fun createLink(innsendingsId: String, opprettEttersendingsLink: Boolean): String {
		// Eksempler:
		// Fortsett senere: https://tjenester-q1.nav.no/dokumentinnsending/oversikt/10014Qi1G For å gjenoppta påbegynt søknad
		// Ettersendingslink: https://tjenester-q1.nav.no/dokumentinnsending/ettersendelse/10010WQEF For å ettersende vedlegg på tidligere innsendt søknad (10010WQEF)
		return if (opprettEttersendingsLink)
			kafkaConfig.tjenesteUrl + linkDokumentinnsendingEttersending + innsendingsId  // Nytt endepunkt
		else
			kafkaConfig.tjenesteUrl + linkDokumentinnsending + innsendingsId
	}
}
