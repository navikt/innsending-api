package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.dto.AktivSakDto
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.InnsendtVedleggDto
import no.nav.soknad.innsending.dto.VedleggDto
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
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, it will return DokumentSoknadDto which contain title and url to where schema for main document can be found " )])
    @PostMapping("/opprettSoknad")
    fun opprettSoknad(@RequestParam skjemanr: String, @RequestParam sprak: String, @RequestParam brukerId: String): ResponseEntity<DokumentSoknadDto> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(lagDummySoknad(skjemanr, null, sprak, brukerId, null))
    }

    @Operation(summary = "Requests creating a new application with reference to a prior sent in application.", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, it will return DokumentSoknadDto which contain title, and allows for user to add additional attachment." )])
    @PostMapping("/opprettEttersending")
    fun opprettEttersending(@RequestParam ettersendingTilBehandlingsId: String, @RequestParam brukerId: String): ResponseEntity<DokumentSoknadDto> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(lagDummySoknad("NAV 10-07.73", null, "NO", brukerId, ettersendingTilBehandlingsId))
    }

    @Operation(summary = "Requests fetching a previously created application.", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, it will return DokumentSoknadDto which contain title, and list of planned and uploaded attachments." )])
    @PostMapping("/hentSoknad")
    fun hentSoknad(@RequestParam behandlingsId: String): ResponseEntity<DokumentSoknadDto> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(lagDummySoknad("NAV 10-07.73", null, "NO", "12345678901", null))
    }

    @Operation(summary = "Requests updating a previously created application.", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, the application is stored and an updated version of the DokumentSoknadDto is returned." )])
    @PatchMapping("/oppdaterSoknad")
    fun lagreSoknad(@RequestBody dokumentSoknadDto: DokumentSoknadDto): ResponseEntity<DokumentSoknadDto> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(dokumentSoknadDto)
    }

    @Operation(summary = "Requests adding av new attachment to a previously created application.", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, the attachment is stored and an updated version of the VedleggDto is returned." )])
    @PostMapping("/lagreNyttVedlegg")
    fun lagreVedlegg(@RequestParam behandlingsId: String, @RequestParam skjemanr: String?, @RequestParam vedleggsnr: String?, @RequestParam tittel: String, @RequestParam vedlegg: ByteArray): ResponseEntity<VedleggDto> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(lagDummyVedlegg(skjemanr ?: "NAV 10-07.73"))
    }

    @Operation(summary = "Requests update to one of the application's attachments.", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, update of the attachment is stored and an updated version of the VedleggDto is returned." )])
    @PatchMapping("/lagreVedlegg")
    fun lagreVedlegg(@RequestBody vedleggDto: VedleggDto): ResponseEntity<VedleggDto> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(vedleggDto)
    }

    @Operation(summary = "Requests delete to one of the application's attachments. Not delete of main attachment will be rejected", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, the attachment is deleted from the database and a confirmation string is returned." )])
    @DeleteMapping("/slettVedlegg")
    fun slettVedlegg(@RequestParam vedleggId: Long): ResponseEntity<String> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body("Slettet $vedleggId")
    }

    @Operation(summary = "Requests delete of an application.", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, the application is deleted from the database and a confirmation string is returned." )])
    @DeleteMapping("/slettSoknad")
    fun slettSoknad(@RequestParam soknadId: Long): ResponseEntity<String> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body("Slettet $soknadId")
    }


    @Operation(summary = "Requests that the application shall be sent to NAV.", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, the application is sent to NAV, and a confirmation string is returned." )])
    @PostMapping("/sendInnSoknad")
    fun sendInnSoknad(@RequestParam soknadId: String): ResponseEntity<String> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body("Soknad $soknadId er sendt inn")
    }

    @Operation(summary = "Requests fetching list of active applications that have been sent to NAV by the applicant.", tags = ["operations"])
    @ApiResponses(value = [ApiResponse(responseCode = "200", description = "If successfull, a list of already sent in applications is returned." )])
    @GetMapping("/hentAktiveSaker")
    fun aktiveSaker(@RequestParam brukerId: String, @RequestParam skjemanr: String?): ResponseEntity<List<AktivSakDto>> {

        return ResponseEntity
            .status(HttpStatus.OK)
            .body(listOf(lagDummyAktivSak("123", "NAV 10-07.73", "Tema", false)))
    }

    private fun lagDummySoknad(skjemanr: String, vedleggsnr: String?, sprak: String, brukerId: String, ettersendingId: String?): DokumentSoknadDto =
        DokumentSoknadDto(1L, UUID.randomUUID().toString(), ettersendingId, brukerId, skjemanr, "Tittel", "Test","NO", SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null,
            listOf(lagDummyVedlegg(vedleggsnr)))

    private fun lagDummyVedlegg(vedleggsnr: String?): VedleggDto =
       VedleggDto(1L, vedleggsnr, "Tittel", "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/dfdcae6ba6ac611e9a67d013fc2fe712645fb937.pdf", UUID.randomUUID().toString(), null, null, true, false, true
           , OpplastingsStatus.IkkeLastetOpp, LocalDateTime.now())

    private fun lagDummyAktivSak(behandlingsId: String?, skjemanr: String, tema: String, ettersending: Boolean) =
        AktivSakDto(behandlingsId, skjemanr, "Tittel", tema, LocalDateTime.now(), ettersending, listOf(
            lagDummyInnsendtVedleggDto()))

    private fun lagDummyInnsendtVedleggDto() = InnsendtVedleggDto("NAV 10-07.73", "Tittel")

}