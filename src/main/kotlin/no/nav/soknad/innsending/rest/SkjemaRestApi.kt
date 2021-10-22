package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.dto.SkjemaDokumentSoknadTransformer
import no.nav.soknad.innsending.dto.SkjemaDto
import no.nav.soknad.innsending.service.SoknadService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/fyllUt")
class SkjemaRestApi(val soknadService: SoknadService) {

	@Operation(summary = "Requests to add attachment and route to dialog for sending application to NAV.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "Application is stored and applicant is guided to page to upload additional attachments if required else applicant is guided to summary page before committing it to NAV."
	)])
	@PostMapping("/leggTilVedlegg")
	fun soknadFraFyllUt(@RequestBody skjemaDto: SkjemaDto): ResponseEntity<String> {

		soknadService.opprettEllerOppdaterSoknad(SkjemaDokumentSoknadTransformer(skjemaDto).apply())
		return ResponseEntity
			.status(HttpStatus.OK)
			.body("Opprettet med innsendingsId X")
	}

}


