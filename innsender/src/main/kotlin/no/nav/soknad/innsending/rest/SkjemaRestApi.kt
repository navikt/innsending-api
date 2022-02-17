package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.dto.SkjemaDokumentSoknadTransformer
import no.nav.soknad.innsending.dto.SkjemaDto
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import org.hibernate.annotations.common.util.impl.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/fyllUt")
class SkjemaRestApi(val restConfig: RestConfig, val soknadService: SoknadService, val innsenderMetrics: InnsenderMetrics) {

	private val logger = LoggerFactory.logger(javaClass)

	@Operation(summary = "Requests to create application and redirect client to frontend application for adding attachments and sending application to NAV.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "302",
		description = "Application is stored and applicant is redirected to page to upload additional attachments if required else applicant is guided to summary page before committing it to NAV."
	)])
	@PostMapping("/leggTilVedlegg")
	fun soknadFraFyllUt(@RequestBody skjemaDto: SkjemaDto): ResponseEntity<Void> {
		logger.info("Kall fra FyllUt for å opprette søknad for skjema ${skjemaDto.skjemanr}")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.OPPRETT.name)
		try {
			val opprettetSoknadId = soknadService.opprettNySoknad(SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(skjemaDto))
			return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(restConfig.frontEndFortsettEndpoint+opprettetSoknadId)).build()
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

}


