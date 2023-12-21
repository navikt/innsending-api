package no.nav.soknad.innsending.rest.ekstern

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.EksternApi
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EksternOpprettEttersending
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.EttersendingService
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = Constants.TOKENX, claimMap = [Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH])
class EksternRestApi(
	private val tilgangskontroll: Tilgangskontroll,
	private val ettersendingService: EttersendingService,
) : EksternApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	override fun eksternOpprettEttersending(eksternOpprettEttersending: EksternOpprettEttersending): ResponseEntity<DokumentSoknadDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log(
			"Kall for å opprette ettersending på skjema ${eksternOpprettEttersending.skjemanr}",
			brukerId
		)

		ettersendingService.logWarningForExistingEttersendelse(
			brukerId = brukerId,
			skjemanr = eksternOpprettEttersending.skjemanr,
		)

		val ettersending = ettersendingService.externalCreateEttersending(
			eksternOpprettEttersending = eksternOpprettEttersending,
			brukerId = brukerId
		)

		combinedLogger.log(
			"${ettersending.innsendingsId}: Opprettet ettersending fra ekstern applikasjon på skjema ${ettersending.skjemanr}",
			brukerId
		)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ettersending)
	}


}
