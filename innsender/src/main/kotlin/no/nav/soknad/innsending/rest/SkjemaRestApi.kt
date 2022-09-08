package no.nav.soknad.innsending.rest

import io.swagger.annotations.ApiOperation
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
@ProtectedWithClaims(issuer = TOKENX, claimMap = [CLAIM_ACR_LEVEL_4])
class SkjemaRestApi(val restConfig: RestConfig,
										val soknadService: SoknadService,
										val innsenderMetrics: InnsenderMetrics,
										val tilgangskontroll: Tilgangskontroll)
	: FyllUtApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@ApiOperation(
		value = "Motta ferdig utfylt søknad og metadata til denne.",
		nickname = "fyllUt",
		notes = "FyllUt tjenesten gir søker mulighet til å fylle ut en søknad. Når vedkommende er ferdig med dette kalles dette endepunktet for å mellomlagre søknaden og gi søker mulighet til å laste opp eventuelle vedlegg og sende inn søknaden og disse til NAV.")
	@io.swagger.annotations.ApiResponses(
		value = [io.swagger.annotations.ApiResponse(code = 302, message = "Found")])
	@RequestMapping(
		method = [RequestMethod.POST],
		value = ["/fyllUt/v1/leggTilVedlegg"],
		consumes = ["application/json"]
	)
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


