package no.nav.soknad.innsending.rest.ekstern

import no.nav.soknad.innsending.api.EksternApi
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EksternOpprettEttersending
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.EttersendingService
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

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

		// FIXME: Do we need to log warning if ettersending already exists?

		val ettersending = ettersendingService.externalCreateEttersending(
			eksternOpprettEttersending = eksternOpprettEttersending,
			brukerId = brukerId
		)

		combinedLogger.log(
			"${ettersending.innsendingsId}: Opprettet ettersending fra soknadsveiviser på skjema ${ettersending.skjemanr}",
			brukerId
		)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ettersending)
	}


}
