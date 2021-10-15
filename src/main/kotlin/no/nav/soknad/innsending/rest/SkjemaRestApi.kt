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
@RequestMapping("/innsending")
class SkjemaRestApi(val soknadService: SoknadService) {

	@Operation(summary = "Requests to send application to NAV.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, the application is sent to NAV to be archived and handled by case worker. If successful ok text is returned."
	)])
	@PostMapping("/soknad")
	fun sendInnSoknad(@RequestBody skjemaDto: SkjemaDto): ResponseEntity<String> {

		soknadService.opprettEllerOppdaterSoknad(SkjemaDokumentSoknadTransformer(skjemaDto).apply())
		return ResponseEntity
			.status(HttpStatus.OK)
			.body("Videresendt med behandlingsId X")
	}

}


