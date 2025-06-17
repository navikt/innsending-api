package no.nav.soknad.innsending.rest.soknadsveiviser


import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.SoknadsveiviserApi
import no.nav.soknad.innsending.brukernotifikasjon.NotificationOptions
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.OpprettEttersendingGittSkjemaNr
import no.nav.soknad.innsending.model.OpprettSoknadBody
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.EttersendingService
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RestController

// Soknadsveiviser (old dokumentinnsending) creates a link to sendinn-frontend with query parameters to create a new soknad/ettersendelse
// Since soknadsveiviser is deprecated and will be removed, it is separated from the sendinn folder
@RestController
@CrossOrigin(maxAge = 3600)
@ProtectedWithClaims(
	issuer = Constants.TOKENX,
	claimMap = [Constants.CLAIM_ACR_LEVEL_4, Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH],
	combineWithOr = true
)
class SoknadsveiviserRestApi(
	private val tilgangskontroll: Tilgangskontroll,
	private val ettersendingService: EttersendingService,
	private val notificationService: NotificationService,
) : SoknadsveiviserApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	override fun opprettSoknad(
		opprettSoknadBody: OpprettSoknadBody,
		navEnvQualifier: EnvQualifier?,
	): ResponseEntity<DokumentSoknadDto> {
		throw UnsupportedOperationException("Opprettelse av søknad med visningstype dokumentinnsending er ikke støttet lenger.")
	}

	@Timed(InnsenderOperation.OPPRETT)
	override fun opprettEttersendingGittSkjemanr(
		opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr,
		navEnvQualifier: EnvQualifier?,
	): ResponseEntity<DokumentSoknadDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log(
			"Kall for å opprette ettersending fra soknadsveiviser (via sendinn) på skjema ${opprettEttersendingGittSkjemaNr.skjemanr} ($navEnvQualifier)",
			brukerId
		)

		ettersendingService.logWarningForExistingEttersendelse(
			brukerId = brukerId,
			skjemanr = opprettEttersendingGittSkjemaNr.skjemanr,
		)

		val ettersending = ettersendingService.createEttersendingFromExistingSoknaderUsingSanity(
			opprettEttersendingGittSkjemaNr = opprettEttersendingGittSkjemaNr,
			brukerId = brukerId
		)

		combinedLogger.log(
			"${ettersending.innsendingsId}: Opprettet ettersending fra soknadsveiviser (via sendinn) på skjema ${ettersending.skjemanr}",
			brukerId
		)
		notificationService.create(ettersending.innsendingsId!!, NotificationOptions(envQualifier = navEnvQualifier))

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ettersending)
	}

}

