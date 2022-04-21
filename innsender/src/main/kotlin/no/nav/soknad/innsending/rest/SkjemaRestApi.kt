package no.nav.soknad.innsending.rest

import io.swagger.annotations.ApiParam
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.api.FyllUtApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import javax.validation.Valid
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.util.Constants.CLAIM_ACR_LEVEL_4
import no.nav.soknad.innsending.util.Constants.TOKENX

@RestController
@RequestMapping("/fyllUt/v1")
@ProtectedWithClaims(issuer = TOKENX, claimMap = [CLAIM_ACR_LEVEL_4])
class SkjemaRestApi(val restConfig: RestConfig,
										val soknadService: SoknadService,
										val innsenderMetrics: InnsenderMetrics,
										val tilgangskontroll: Tilgangskontroll)
	: FyllUtApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Operation(summary = "Requests to create application and redirect client to frontend application for adding attachments and sending application to NAV.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "302",
		description = "Application is stored and applicant is redirected to page to upload additional attachments if required else applicant is guided to summary page before committing it to NAV."
	)])
	@PostMapping("/leggTilVedlegg")
	override fun fyllUt(
		@ApiParam(
			required = true,
			value = "Søknadsmetadata og søknad som PDF og Json samt liste over eventuelle obligatoriske og valgfrie vedlegg som skal/kan legges til."
		) @Valid @RequestBody skjemaDto: no.nav.soknad.innsending.model.SkjemaDto
	): ResponseEntity<Unit> {
		logger.info("Kall fra FyllUt for å opprette søknad for skjema ${skjemaDto.skjemanr}")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.OPPRETT.name)
		try {
			val brukerId = tilgangskontroll.hentBrukerFraToken()
			val opprettetSoknadId = soknadService.opprettNySoknad(SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(skjemaDto, brukerId))
			return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(restConfig.frontEndFortsettEndpoint+opprettetSoknadId)).build()
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

}


