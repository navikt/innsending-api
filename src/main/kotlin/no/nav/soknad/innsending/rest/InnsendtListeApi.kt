package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.dto.AktivSakDto
import no.nav.soknad.innsending.dto.InnsendtVedleggDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/innsendte")
class InnsendtListeApi {


	@Operation(summary = "Requests fetching list of active applications that have been sent to NAV by the applicant.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, a list of already sent in applications is returned."
	)])
	@GetMapping("/hentAktiveSaker/{brukerId}")
	fun aktiveSaker(@PathVariable brukerId: String, @RequestParam skjemanr: String?): ResponseEntity<List<AktivSakDto>> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(listOf(lagDummyAktivSak("123", "NAV 10-07.73", "Tema", false)))
	}

	private fun lagDummyAktivSak(innsendingsId: String?, skjemanr: String, tema: String, ettersending: Boolean) =
		AktivSakDto(
			innsendingsId, skjemanr, "Tittel", tema, LocalDateTime.now(), ettersending, listOf(
				lagDummyInnsendtVedleggDto()
			)
		)

	private fun lagDummyInnsendtVedleggDto() = InnsendtVedleggDto("NAV 10-07.73", "Tittel")

}
