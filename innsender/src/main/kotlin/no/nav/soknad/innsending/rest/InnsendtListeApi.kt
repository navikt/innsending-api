package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.api.InnsendteApi
import no.nav.soknad.innsending.model.AktivSakDto
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SafService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import org.hibernate.annotations.common.util.impl.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/innsendte")
class InnsendtListeApi(
	val safService: SafService, val tilgangskontroll: Tilgangskontroll, val innsenderMetrics: InnsenderMetrics) :
	InnsendteApi {

	private val logger = LoggerFactory.logger(javaClass)

	@Operation(summary = "Requests fetching list of applications that have been sent to NAV by the applicant.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, a list of already sent in applications is returned."
	)])
	@GetMapping("/hentAktiveSaker")
	override fun aktiveSaker(): ResponseEntity<List<AktivSakDto>> {

		logger.info("Kall for å hente innsendte søknader for en bruker")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.OPPRETT.name)
		try {
			val innsendteSoknader = safService.hentInnsendteSoknader(tilgangskontroll.hentBrukerFraToken(null))
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(innsendteSoknader)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}


}
