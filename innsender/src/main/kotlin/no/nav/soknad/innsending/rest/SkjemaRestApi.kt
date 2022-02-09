package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.dto.SkjemaDokumentSoknadTransformer
import no.nav.soknad.innsending.dto.SkjemaDto
import no.nav.soknad.innsending.service.SoknadService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/fyllUt")
class SkjemaRestApi(val restConfig: RestConfig, val soknadService: SoknadService) {

	@Operation(summary = "Requests to create application and redirect client to frontend application for adding attachments and sending application to NAV.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "302",
		description = "Application is stored and applicant is redirected to page to upload additional attachments if required else applicant is guided to summary page before committing it to NAV."
	)])
	@PostMapping("/leggTilVedlegg")
	fun soknadFraFyllUt(@RequestBody skjemaDto: SkjemaDto): ResponseEntity<Void> {

		val opprettetSoknadId = soknadService.opprettNySoknad(SkjemaDokumentSoknadTransformer(skjemaDto).apply())
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(restConfig.frontEndFortsettEndpoint+opprettetSoknadId)).build()
	}

}


