package no.nav.soknad.innsending.rest.ekstern

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.EksternApi
import no.nav.soknad.innsending.model.BodyStatusResponseDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EksternOpprettEttersending
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.EttersendingService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(
	issuer = Constants.TOKENX,
	claimMap = [Constants.CLAIM_ACR_LEVEL_4, Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH],
	combineWithOr = true
)
class EksternRestApi(
	private val tilgangskontroll: Tilgangskontroll,
	private val ettersendingService: EttersendingService,
	private val soknadService: SoknadService,
	private var subjectHandler: SubjectHandlerInterface
) : EksternApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	override fun eksternOpprettEttersending(
		eksternOpprettEttersending: EksternOpprettEttersending,
		navCallId: String?,
	): ResponseEntity<DokumentSoknadDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		val applikasjon = subjectHandler.getClientId()

		combinedLogger.log(
			"[${applikasjon}] - Kall for å opprette ettersending fra ekstern applikasjon på skjema ${eksternOpprettEttersending.skjemanr}",
			brukerId
		)

		ettersendingService.logWarningForExistingEttersendelse(
			brukerId = brukerId,
			skjemanr = eksternOpprettEttersending.skjemanr,
		)

		val ettersending = ettersendingService.createEttersendingFromExternalApplication(
			eksternOpprettEttersending = eksternOpprettEttersending,
			brukerId = brukerId,
		)

		combinedLogger.log(
			"[${applikasjon}] - ${ettersending.innsendingsId}: Opprettet ettersending fra ekstern applikasjon på skjema ${ettersending.skjemanr}",
			brukerId
		)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ettersending)
	}

	override fun eksternSlettEttersending(
		innsendingsId: String,
		navCallId: String?
	): ResponseEntity<BodyStatusResponseDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		val applikasjon = subjectHandler.getClientId()

		combinedLogger.log(
			"[${applikasjon}] - $innsendingsId: Kall for å slette ettersending fra ekstern applikasjon",
			brukerId
		)

		val ettersending = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.validateSoknadAccess(ettersending)

		soknadService.deleteSoknadFromExternalApplication(ettersending, applikasjon)

		combinedLogger.log("[${applikasjon}] - $innsendingsId: Slettet ettersending fra ekstern applikasjon", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet ettersending med id $innsendingsId"))

	}
}
