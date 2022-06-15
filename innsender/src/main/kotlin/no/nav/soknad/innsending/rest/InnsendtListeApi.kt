package no.nav.soknad.innsending.rest

import io.swagger.annotations.ApiOperation
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.api.Unprotected
import no.nav.soknad.innsending.api.InnsendteApi
import no.nav.soknad.innsending.model.AktivSakDto
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SafService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@ProtectedWithClaims(issuer = Constants.TOKENX, claimMap = [Constants.CLAIM_ACR_LEVEL_4])
class InnsendtListeApi(
	val safService: SafService, val tilgangskontroll: Tilgangskontroll, val innsenderMetrics: InnsenderMetrics) :
	InnsendteApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@ApiOperation(
		value = "Hente liste over allerede innsendte søknader.",
		nickname = "aktiveSaker",
		notes = "For å hjelpe søker til å finne fram relevant søknad han/hun ønsker å få ettersendt dokumenter på, skal det være mulig å hente ut en liste av innsendte søknader.",
		response = AktivSakDto::class,
		responseContainer = "List")
	@io.swagger.annotations.ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Vellykket operasjon. Liste av søkers innsendte søknader returneres.",
			response = AktivSakDto::class,
			responseContainer = "List"
		), io.swagger.annotations.ApiResponse(code = 404, message = "Ingen innsendte søknader funnet.")])
	@RequestMapping(
		method = [RequestMethod.GET],
		value = ["/innsendte/v1/hentAktiveSaker"],
		produces = ["application/json"]
	)
	override fun aktiveSaker(): ResponseEntity<List<AktivSakDto>> {

		logger.info("Kall for å hente innsendte søknader for en bruker")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.OPPRETT.name)
		try {
			val innsendteSoknader = safService.hentInnsendteSoknader(tilgangskontroll.hentBrukerFraToken())
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(innsendteSoknader)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

}
