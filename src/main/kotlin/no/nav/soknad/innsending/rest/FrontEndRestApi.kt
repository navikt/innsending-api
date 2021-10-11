package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.dto.*
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/frontend")
class FrontEndRestApi {

	@Operation(summary = "Requests creating a new application given main document id (skjemanr).", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return DokumentSoknadDto which contains the title and url to where the " +
			"schema for the main document can be found."
	)])
	@PostMapping("/soknad")
	fun opprettSoknad(
		@RequestParam skjemanr: String,
		@RequestParam sprak: String,
		@RequestParam brukerId: String
	): ResponseEntity<DokumentSoknadDto> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(lagDummySoknad(skjemanr, null, sprak, brukerId, null))
	}

	@Operation(summary = "Requests creating a new application with reference to a prior sent in application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return DokumentSoknadDto which contains the title, and allows for a user " +
			"to add additional attachments."
	)])
	@PostMapping("/ettersending")
	fun opprettEttersending(
		@RequestParam ettersendingTilBehandlingsId: String,
		@RequestParam brukerId: String
	): ResponseEntity<DokumentSoknadDto> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(lagDummySoknad("NAV 10-07.73", null, "NO", brukerId, ettersendingTilBehandlingsId))
	}

	@Operation(summary = "Requests fetching a previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, it will return DokumentSoknadDto which contains the title, and a list of planned " +
				"and uploaded attachments."
	)])
	@GetMapping("/soknad/{behandlingsId}")
	fun hentSoknad(@PathVariable behandlingsId: String): ResponseEntity<DokumentSoknadDto> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(lagDummySoknad("NAV 10-07.73", null, "NO", "12345678901", null))
	}

	@Operation(summary = "Requests fetching a list of attachments to previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return VedleggsListeDto which contains list of attachments to the specified application."
	)])
	@GetMapping("/soknad/{behandlingsId}/vedlegg")
	fun hentVedleggsListe(@PathVariable behandlingsId: String): ResponseEntity<VedleggsListeDto> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(lagDummyVedleggsListe())
	}

	@Operation(summary = "Requests fetching a specified attachment to a created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return the specified VedleggDto."
	)])
	@GetMapping("/soknad/{behandlingsId}/vedlegg/{vedleggsId}")
	fun hentVedlegg(@PathVariable behandlingsId: String, @PathVariable vedleggsId: String): ResponseEntity<VedleggDto> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(lagDummyVedlegg(vedleggsId))
	}

	@Operation(summary = "Requests updating a previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the application is stored and an updated version of the DokumentSoknadDto is returned."
	)])
	@PostMapping("/soknad/{behandlingsId}")
	fun lagreSoknad(@PathVariable behandlingsId: String, @RequestBody dokumentSoknadDto: DokumentSoknadDto): ResponseEntity<DokumentSoknadDto> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(dokumentSoknadDto)
	}

	// Hvis det er et nytt vedlegg, så vil ikke frontend ha vedleggsid da dette settes av backend ved oppretting av resssurs.
	// Må derfor legge inn dummy id (f.eks. -1)?.
	@Operation(summary = "Requests adding or updating attachment to a previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the attachment is stored and an updated version of the VedleggDto is returned."
	)])
	@PostMapping("/soknad/{behandlingsId}/vedlegg/{vedleggsId}")
	fun lagreVedlegg(
		@PathVariable behandlingsId: String,
		@RequestBody vedlegg: VedleggDto
	): ResponseEntity<VedleggDto> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(lagDummyVedlegg(vedlegg.vedleggsnr ?: "N6"))
	}

	@Operation(
		summary = "Requests delete to one of the application's attachments.",
		tags = ["operations"]
	)
	@ApiResponses(
		value = [ApiResponse(responseCode = "200",
			description = "If successful, the attachment is deleted from the database and a confirmation string is returned."
	)])
	@DeleteMapping("/soknad/{behandlingsId}/vedlegg/{vedleggsId}")
	fun slettVedlegg(@PathVariable vedleggId: Long): ResponseEntity<String> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body("Slettet $vedleggId")
	}

	@Operation(summary = "Requests delete of an application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the application is deleted from the database and a confirmation string is returned."
	)])
	@DeleteMapping("/soknad/{behandlingsId}")
	fun slettSoknad(@PathVariable behandlingsId: Long): ResponseEntity<String> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body("Slettet $behandlingsId")
	}


	@Operation(summary = "Requests that the application shall be sent to NAV.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the application is sent to NAV, and a confirmation string is returned."
	)])
	@PostMapping("/sendInn/{behandlingsId}")
	fun sendInnSoknad(@PathVariable behandlingsId: String): ResponseEntity<String> {

		return ResponseEntity
			.status(HttpStatus.OK)
			.body("Soknad $behandlingsId er sendt inn")
	}

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



// Midlertidige dummy metoder
	private fun lagDummySoknad(skjemanr: String, vedleggsnr: String?, sprak: String, brukerId: String, ettersendingId: String?) =
			DokumentSoknadDto(1L, UUID.randomUUID().toString(), ettersendingId, brukerId, skjemanr, "Tittel", "Test", "NO",
				SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null, listOf(lagDummyVedlegg(vedleggsnr)))

	private fun lagDummyVedlegg(vedleggsnr: String?) =
		 VedleggDto(1L, vedleggsnr, "Tittel", "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/dfdcae6ba6ac611e9a67d013fc2fe712645fb937.pdf",
			 UUID.randomUUID().toString(), null, null, true, false, true, OpplastingsStatus.IkkeLastetOpp, LocalDateTime.now())

	private fun lagDummyAktivSak(behandlingsId: String?, skjemanr: String, tema: String, ettersending: Boolean) =
		AktivSakDto(
			behandlingsId, skjemanr, "Tittel", tema, LocalDateTime.now(), ettersending, listOf(
				lagDummyInnsendtVedleggDto()
			)
		)

	private fun lagDummyVedleggsListe() = VedleggsListeDto(listOf("1234567890"))

	private fun lagDummyInnsendtVedleggDto() = InnsendtVedleggDto("NAV 10-07.73", "Tittel")
}
