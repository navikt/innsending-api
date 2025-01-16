package no.nav.soknad.innsending.rest.ekstern

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.EksternoppgaveApi
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.EttersendingService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@ProtectedWithClaims(
	issuer = Constants.AZURE,
	// TODO claimMap = ["scp=defaultaccess oppgave-initiering"]
)
class InternInitiertOppgaver(
	private val tilgangskontroll: Tilgangskontroll,
	private val ettersendingService: EttersendingService,
	private val soknadService: SoknadService,
	private var subjectHandler: SubjectHandlerInterface
): EksternoppgaveApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	override fun eksternEttersendingsOppgave(
		eksternEttersendingsOppgave: EksternEttersendingsOppgave,
		navCallId: String?): ResponseEntity<DokumentSoknadDto> {

		val brukerId = eksternEttersendingsOppgave.brukerId
		val applikasjon = subjectHandler.getClientId()

		combinedLogger.log(
			"[${applikasjon}] - Kall for å opprette ettersending fra ekstern applikasjon på skjema ${eksternEttersendingsOppgave.skjemanr}",
			brukerId
		)

		ettersendingService.logWarningForExistingEttersendelse(
			brukerId = brukerId,
			skjemanr = eksternEttersendingsOppgave.skjemanr,
		)

		val eksternOpprettEttersending = EksternOpprettEttersending(
			skjemanr = eksternEttersendingsOppgave.skjemanr,
			sprak = eksternEttersendingsOppgave.sprak,
			tema = eksternEttersendingsOppgave.tema,
			vedleggsListe = eksternEttersendingsOppgave.vedleggsListe,
			tittel = eksternEttersendingsOppgave.tittel,
			brukernotifikasjonstype = eksternEttersendingsOppgave.brukernotifikasjonstype,
			koblesTilEksisterendeSoknad = eksternEttersendingsOppgave.koblesTilEksisterendeSoknad
		)

		val ettersending = ettersendingService.createEttersendingFromExternalApplication(
			eksternOpprettEttersending = eksternOpprettEttersending,
			brukerId = brukerId,
			erNavInitiert = true
		)

		combinedLogger.log(
			"[${applikasjon}] - ${ettersending.innsendingsId}: Opprettet ettersending fra ekstern applikasjon på skjema ${ettersending.skjemanr}",
			brukerId
		)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ettersending)
	}

	override fun eksternOppgaveSlett(innsendingsId: String, navCallId: String?): ResponseEntity<BodyStatusResponseDto> {
		val applikasjon = subjectHandler.getClientId()
		logger.info("[${applikasjon}] - Kall for å slette søknad/ettersending $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)

		combinedLogger.log(
			"[${applikasjon}] - Kall for å slette søknad/ettersending fra ekstern applikasjon på skjema ${innsendingsId}",
			soknadDto.brukerId
		)

		soknadService.deleteSoknadFromExternalApplication(soknadDto)

		combinedLogger.log("[${applikasjon}] - $innsendingsId: Slettet søknad/ettersending fra ekstern applikasjon", soknadDto.brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet ettersending med id $innsendingsId"))
	}

	override fun oppgaveHentSoknaderForSkjemanr(skjemanr: String, body: String, soknadstyper: kotlin.collections.List<SoknadType>?, navCallId: String?)
	: ResponseEntity<List<DokumentSoknadDto>> {
		val brukerIds = listOf(body)
		val applikasjon = subjectHandler.getClientId()

		brukerIds.forEach {
			combinedLogger.log(
				"[${applikasjon}] - Henter søknader/ettersendinger med $skjemanr for bruker. Soknadstyper=${soknadstyper ?: "ikke spesifisert"}",
				it
			)
		}

		val typeFilter = soknadstyper?.toTypedArray() ?: emptyArray()
		brukerIds.forEach {
			combinedLogger.log(
				"[${applikasjon}] - Henter søknader med søknadstyper=${soknadstyper ?: "<alle>"} for $skjemanr",
				it
			)
		}
		val soknader = brukerIds.flatMap { soknadService.hentAktiveSoknader(it, skjemanr, *typeFilter) }

		return ResponseEntity.status(HttpStatus.OK).body(soknader)
	}

}
