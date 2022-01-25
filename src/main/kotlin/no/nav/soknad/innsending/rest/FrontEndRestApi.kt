package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.dto.*
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.pdfutilities.KonverterTilPdf
import no.nav.soknad.pdfutilities.Validerer
import org.hibernate.annotations.common.util.impl.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/frontend")
class FrontEndRestApi(val soknadService: SoknadService
, private val restConfig: RestConfig) {

	private val logger = LoggerFactory.logger(javaClass)

	@Operation(summary = "Requests creating a new application given main document id (skjemanr).", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return DokumentSoknadDto which contains the title and url to where the " +
			"schema for the main document can be found."
	)])
	@PostMapping("/soknad")
	fun opprettSoknad(@RequestBody opprettSoknad: OpprettSoknadBody
	): ResponseEntity<DokumentSoknadDto> {

		val dokumentSoknadDto = soknadService.opprettSoknad(opprettSoknad.brukerId, opprettSoknad.skjemanr, opprettSoknad.sprak, opprettSoknad.vedleggsListe ?: emptyList())
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(dokumentSoknadDto)
	}

	@Operation(summary = "Requests creating a new application with reference to a prior sent in application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return DokumentSoknadDto which contains the title, and allows for a user " +
			"to add additional attachments."
	)])
	@PostMapping("/ettersendingPaInnsendingsId")
	fun opprettEttersendingGittInnsendingsId(@RequestBody opprettEttersending: OpprettEttersendingGittInnsendingsId
	): ResponseEntity<DokumentSoknadDto> {

		val dokumentSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(opprettEttersending.brukerId, opprettEttersending.ettersendingTilinnsendingsId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(dokumentSoknadDto)
	}

	@Operation(summary = "Requests creating a new application referencing a previous sent inn application by schema number.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return DokumentSoknadDto which contains the title and list of required attachment."
	)])
	@PostMapping("/ettersendPaSkjema")
	fun opprettEttersendingGittSkjemanr(@RequestBody opprettEttersending: OpprettEttersendingGittSkjemaNr
	): ResponseEntity<DokumentSoknadDto> {

		val dokumentSoknadDto = soknadService.opprettSoknadForEttersendingGittSkjemanr(
			opprettEttersending.brukerId, opprettEttersending.skjemanr, opprettEttersending.sprak, opprettEttersending.vedleggsListe ?: emptyList())
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(dokumentSoknadDto)
	}

	@Operation(summary = "Requests fetching a previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, it will return DokumentSoknadDto which contains the title, and a list of planned " +
				"and uploaded attachments."
	)])
	@GetMapping("/soknad/{innsendingsId}")
	fun hentSoknad(@PathVariable innsendingsId: String): ResponseEntity<DokumentSoknadDto> {
		try {
			logger.info("Kall for å hente søknad med id ${innsendingsId}")
			val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll(dokumentSoknadDto, null)
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(dokumentSoknadDto)
		} catch (e: Throwable) {
			logger.error("Feil ved henting av søknad med id ${innsendingsId}, ${e.message}")
			return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(emptyDokumentSoknad(innsendingsId))
		}
	}

	@Operation(summary = "Requests fetching a list of attachments to previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return VedleggsListeDto which contains list of attachments to the specified application."
	)])
	@GetMapping("/soknad/{innsendingsId}/vedlegg")
	fun hentVedleggsListe(@PathVariable innsendingsId: String): ResponseEntity<List<VedleggDto>> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		val vedleggsListeDto = soknadService.hentSoknad(innsendingsId).vedleggsListe
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(vedleggsListeDto)
	}

	@Operation(summary = "Requests fetching a specified attachment to a created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return the specified VedleggDto."
	)])
	@GetMapping("/soknad/{innsendingsId}/vedlegg/{vedleggsId}")
	fun hentVedlegg(@PathVariable innsendingsId: String, @PathVariable vedleggsId: String): ResponseEntity<VedleggDto> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		val vedleggDto = soknadService.hentSoknad(innsendingsId).vedleggsListe.filter { it.id.toString().equals(vedleggsId) }.first()
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(vedleggDto)
	}


	// Hvis det er et nytt vedlegg, så vil ikke frontend ha vedleggsid da dette settes av backend ved oppretting av resssurs.
	// Må derfor legge inn dummy id (f.eks. -1)?.
	@Operation(summary = "Requests adding or updating attachment to a previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the attachment is stored and an updated version of the VedleggDto is returned."
	)])
	@PostMapping("/soknad/{innsendingsId}/vedlegg/{vedleggsId}")
	fun lagreVedlegg(
		@PathVariable innsendingsId: String,
		@RequestBody vedlegg: VedleggDto
	): ResponseEntity<VedleggDto> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		val vedleggDto = soknadService.lagreVedlegg(vedlegg, innsendingsId)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(vedleggDto)
	}

	// Søker skal kunne laste opp ett eller flere filer på ett vedlegg. Dette endepunktet tillater opplasting av en fil.
	@Operation(summary = "Requests adding a file to a specified attachment.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, the file is stored and the allocated id is returned."
	)])
	@RequestMapping(path = ["/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil"], method =[RequestMethod.POST], consumes = [ MediaType.MULTIPART_FORM_DATA_VALUE ])
	fun lagreFil(
		@PathVariable innsendingsId: String,
		@PathVariable vedleggsId: Long,
		@RequestPart file: MultipartFile
	): ResponseEntity<FilIdDto> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		// Ved opplasting av fil skal den valideres (f.eks. lovlig format, summen av størrelsen på filene på et vedlegg må være innenfor max størrelse).
		Validerer().validereFilformat(listOf(file.bytes))
		val fil = KonverterTilPdf().tilPdf(file.bytes)
		val vedleggsFiler = soknadService.hentFiler(innsendingsId, vedleggsId)
		val opplastedeFiler: List<ByteArray> = vedleggsFiler.filter{ it.data != null }.map{ it.data!! }
		Validerer().validerStorrelse((opplastedeFiler + listOf(fil)), restConfig.maxFileSize )

		// Lagre
		val lagretFilDto = soknadService.lagreFil(innsendingsId, FilDto(null, vedleggsId, file.originalFilename ?:"", file.contentType ?: "", fil, LocalDateTime.now()))

		// Alle opplastede filer skal lagres som flatede (dvs. ikke skrivbar PDF) PDFer.
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(FilIdDto(lagretFilDto.id))
	}

	// Søker skal kunne laste opp ett eller flere filer på ett vedlegg. Dette endepunktet tillater opplasting av en fil.
	@Operation(summary = "Requests fetching a specific file uploaded to an attachment.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, the specified file with content is returned."
	)])
	@GetMapping("/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil/{filId}")
	fun hentFil(
		@PathVariable innsendingsId: String,
		@PathVariable vedleggsId: Long,
		@PathVariable filId: Long
	): ResponseEntity<FilDto> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		val filDto = soknadService.hentFil(innsendingsId, vedleggsId, filId)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(filDto)
	}

	@Operation(summary = "Requests fetching information on all uploaded files on an attachment.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, a list with file data is returned. Note the file content itself is not supplied, use /soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil/{id} for that."
	)])
	@GetMapping("/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil")
	fun hentFilInfoForVedlegg(
		@PathVariable innsendingsId: String,
		@PathVariable vedleggsId: Long
	): ResponseEntity<List<FilDto>> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		val filDtoListe = soknadService.hentFiler(innsendingsId, vedleggsId)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(filDtoListe)
	}

	@Operation(summary = "Requests get on a previously uploaded file to an attachment.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, the file is downloaded to the client."
	)])
	@GetMapping("/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil/{filId}")
	fun slettFil(
		@PathVariable innsendingsId: String,
		@PathVariable vedleggsId: Long,
		@PathVariable filId: Long
	): ResponseEntity<BodyStatusResponseDto> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		soknadService.slettFil(innsendingsId, vedleggsId, filId)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet fil med id ${filId}"))
	}

	@Operation(
		summary = "Requests delete to one of the files attached to one of the application's attachments.",
		tags = ["operations"]
	)
	@ApiResponses(
		value = [ApiResponse(responseCode = "200",
			description = "If successful, the attachment is deleted from the database and a confirmation string is returned."
	)])
	@DeleteMapping("/soknad/{innsendingsId}/vedlegg/{vedleggsId}")
	fun slettVedlegg(@PathVariable innsendingsId: String, @PathVariable vedleggsId: Long): ResponseEntity<BodyStatusResponseDto> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		soknadService.slettVedlegg(innsendingsId, vedleggsId)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet vedlegg med id ${vedleggsId}"))
	}

	@Operation(summary = "Requests delete of an application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the application is deleted from the database and a confirmation string is returned."
	)])
	@DeleteMapping("/soknad/{innsendingsId}")
	fun slettSoknad(@PathVariable innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		try {
			logger.info("Kall for å slette søknad med id ${innsendingsId}")
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll(soknadDto, null)
			soknadService.slettSoknadAvBruker(innsendingsId, soknadDto)
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id ${innsendingsId}"))
		} catch (e: Throwable) {
			logger.error("Feil ved sletting av søknad med id ${innsendingsId}, ${e.message}")
			return ResponseEntity
				.status(HttpStatus.NOT_FOUND)
				.body(BodyStatusResponseDto(HttpStatus.NOT_FOUND.name, "Søknaden med id ${innsendingsId} kunne ikke slettes"))
		}
	}


	@Operation(summary = "Requests that the application shall be sent to NAV.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the application is sent to NAV, and a confirmation string is returned."
	)])
	@PostMapping("/sendInn/{innsendingsId}")
	fun sendInnSoknad(@PathVariable innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {

		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll(soknadDto, null)
		soknadService.sendInnSoknad(innsendingsId)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Soknad med id ${innsendingsId} er sendt inn til NAV"))
	}

	private fun tilgangskontroll(soknadDto: DokumentSoknadDto?, brukerId: String?) {
		if (brukerId == null || soknadDto?.brukerId.equals(brukerId)) return //TODO hente ut brukerId fra token
		throw RuntimeException("Søknad finnes ikke eller er ikke tilgjengelig for innlogget bruker")
	}

	private fun emptyDokumentSoknad(innsendingsId: String): DokumentSoknadDto =
		DokumentSoknadDto(null, innsendingsId, null, "", "", "", "", null,
			SoknadsStatus.SlettetAvBruker, LocalDateTime.now(), null, null,emptyList())

	// Midlertidige dummy metoder
	private fun lagDummySoknad(skjemanr: String, vedleggsnr: String?, sprak: String
			, brukerId: String, ettersendingId: String?, vedleggsListe: List<String>?) =
			DokumentSoknadDto(1L, UUID.randomUUID().toString(), ettersendingId, brukerId, skjemanr, "Tittel", "Test", "NO"
				, SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null, lagVedleggsListe(skjemanr, vedleggsListe))

	private fun lagDummyVedlegg(vedleggsnr: String?) =
		 VedleggDto(1L, vedleggsnr ?: "N6", "Tittel",
			 UUID.randomUUID().toString(), null, null, vedleggsnr != null && vedleggsnr.contains("NAV")
			 , false, true
			 , if (vedleggsnr != null && vedleggsnr.length> 2) "https://cdn.sanity.io/files/gx9wf39f/soknadsveiviser-p/dfdcae6ba6ac611e9a67d013fc2fe712645fb937.pdf" else null
			 , OpplastingsStatus.IKKE_VALGT, LocalDateTime.now())

	private fun lagDummyfil(vedleggsId: Long, filId: Long? = 1L) =
		FilDto(filId, vedleggsId, "filnavn", "application/pdf", ByteArray(1), LocalDateTime.now())

	private fun lagVedleggsListe(skjemanr: String, vedleggsListe: List<String>?): List<VedleggDto> {
		val mainListeDto = listOf(lagDummyVedlegg(skjemanr))
 		if (vedleggsListe == null ) return mainListeDto
		val vedleggListeDto: List<VedleggDto> =  vedleggsListe.map { e -> lagDummyVedlegg(e) }.toList()
		return mainListeDto + vedleggListeDto
	}

	private fun lagDummyVedleggsListe() = VedleggsListeDto(listOf("1234567890"))

}
