package no.nav.soknad.innsending.rest.lospost

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.LospostApi
import no.nav.soknad.innsending.location.UrlHandler
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.LospostDto
import no.nav.soknad.innsending.model.OpprettLospost
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.LospostService
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
class LospostRestApi(
	private val tilgangskontroll: Tilgangskontroll,
	private val lospostService: LospostService,
	private val urlHandler: UrlHandler,
) : LospostApi {
	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)

	override fun opprettLospost(
		opprettLospost: OpprettLospost,
		envQualifier: EnvQualifier?
	): ResponseEntity<LospostDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		val (soknadTittel, tema, dokumentTittel, sprak) = opprettLospost
		combinedLogger.log("Skal opprette en innsending for løspost (tema $tema)", brukerId)

		val dto = lospostService.saveLospostInnsending(brukerId, tema, soknadTittel, dokumentTittel, sprak)
		combinedLogger.log("${dto.innsendingsId}: Har opprettet en innsending for løspost", brukerId)
		return ResponseEntity
			.status(HttpStatus.CREATED)
			.location(URI.create(urlHandler.getSendInnUrl(envQualifier) + "/" + dto.innsendingsId))
			.body(dto)
	}
}
