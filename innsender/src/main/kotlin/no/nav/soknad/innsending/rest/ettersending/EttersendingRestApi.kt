package no.nav.soknad.innsending.rest.ettersending


import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.EttersendingApi
import no.nav.soknad.innsending.location.UrlHandler
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.OpprettEttersending
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.EttersendingService
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@ProtectedWithClaims(
	issuer = Constants.TOKENX,
	claimMap = [Constants.CLAIM_ACR_LEVEL_4, Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH],
	combineWithOr = true
)
class EttersendingRestApi(
	private val tilgangskontroll: Tilgangskontroll,
	private val ettersendingService: EttersendingService,
	private val notificationService: NotificationService,
	private val urlHandler: UrlHandler,
) : EttersendingApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	override fun opprettEttersending(
		opprettEttersending: OpprettEttersending,
		envQualifier: EnvQualifier?,
	): ResponseEntity<DokumentSoknadDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log(
			"Kall for å opprette ettersending fra fyllut-ettersending på skjema ${opprettEttersending.skjemanr}",
			brukerId
		)

		ettersendingService.logWarningForExistingEttersendelse(
			brukerId = brukerId,
			skjemanr = opprettEttersending.skjemanr,
		)

		val ettersending = ettersendingService.createEttersendingFromFyllutEttersending(
			ettersending = opprettEttersending,
			brukerId = brukerId
		)

		combinedLogger.log(
			"${ettersending.innsendingsId}: Opprettet ettersending fra fyllut-ettersending på skjema ${ettersending.skjemanr}",
			brukerId
		)

		// TODO envQualifier
		notificationService.create(ettersending.innsendingsId!!)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.location(URI.create(urlHandler.getSendInnUrl(envQualifier) + "/" + ettersending.innsendingsId))
			.body(ettersending)
	}
}

