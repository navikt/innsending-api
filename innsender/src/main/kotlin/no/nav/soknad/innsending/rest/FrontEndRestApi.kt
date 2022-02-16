package no.nav.soknad.innsending.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.dto.*
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.pdfutilities.KonverterTilPdf
import no.nav.soknad.pdfutilities.Validerer
import org.hibernate.annotations.common.util.impl.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime

@RestController
@RequestMapping("/frontend/v1")
class FrontEndRestApi(
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
	private val restConfig: RestConfig) {

	private val logger = LoggerFactory.logger(javaClass)

	@Operation(summary = "Requests creating a new application given main document id (skjemanr).", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return DokumentSoknadDto which contains the title and url to where the " +
			"schema for the main document can be found."
	)])
	@PostMapping("/soknad")
	fun opprettSoknad(@RequestBody opprettSoknad: OpprettSoknadBody
	): ResponseEntity<DokumentSoknadDto> {
		logger.info("Kall for å opprette søknad på skjema ${opprettSoknad.skjemanr}")
		val brukerId = tilgangskontroll.hentBrukerFraToken(opprettSoknad.brukerId)
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerId, opprettSoknad.skjemanr, finnSpraakFraInput(opprettSoknad.sprak), opprettSoknad.vedleggsListe ?: emptyList())
		logger.info("Opprettet søknad ${dokumentSoknadDto.innsendingsId} på skjema ${opprettSoknad.skjemanr}")
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
		logger.info("Kall for å opprette ettersending på søknad ${opprettEttersending.ettersendingTilinnsendingsId}")
		val origSoknad = soknadService.hentSoknad(opprettEttersending.ettersendingTilinnsendingsId)
		val brukerId = tilgangskontroll.hentBrukerFraToken(opprettEttersending.brukerId)
		tilgangskontroll.harTilgang(origSoknad, brukerId)

		val dokumentSoknadDto = soknadService.opprettSoknadForettersendingAvVedlegg(brukerId, opprettEttersending.ettersendingTilinnsendingsId)
		logger.info("Opprettet ettersending ${dokumentSoknadDto.innsendingsId} for innsendingsid ${opprettEttersending.ettersendingTilinnsendingsId}")
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
		logger.info("Kall for å opprette ettersending på skjema ${opprettEttersending.skjemanr}")
		val brukerId = tilgangskontroll.hentBrukerFraToken(opprettEttersending.brukerId)
		val dokumentSoknadDto = soknadService.opprettSoknadForEttersendingGittSkjemanr(
			brukerId, opprettEttersending.skjemanr, finnSpraakFraInput(opprettEttersending.sprak), opprettEttersending.vedleggsListe ?: emptyList())
		logger.info("Opprettet ettersending ${dokumentSoknadDto.innsendingsId} på skjema ${opprettEttersending.skjemanr}")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(dokumentSoknadDto)
	}

	@Operation(summary = "Requests fetching all the aplicant's active applications.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return list of DokumentSoknadDto."
	)])
	@GetMapping("/soknad")
	fun hentAktiveOpprettedeSoknader(): ResponseEntity<List<DokumentSoknadDto>> {
		logger.info("Kall for å hente alle opprette ikke innsendte søknader")
		val brukerIds = tilgangskontroll.hentPersonIdents()
		val dokumentSoknadDtos = soknadService.hentAktiveSoknader(brukerIds)
		logger.info("Hentet søknader opprettet av bruker")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(dokumentSoknadDtos)
	}


	@Operation(summary = "Requests fetching a previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, it will return DokumentSoknadDto which contains the title, and a list of planned " +
				"and uploaded attachments."
	)])
	@GetMapping("/soknad/{innsendingsId}")
	fun hentSoknad(@PathVariable innsendingsId: String): ResponseEntity<DokumentSoknadDto> {
		logger.info("Kall for å hente søknad med id $innsendingsId")
		val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(dokumentSoknadDto)
		logger.info("Hentet søknad ${dokumentSoknadDto.innsendingsId}")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(dokumentSoknadDto)
	}

	@Operation(summary = "Requests fetching a list of attachments to previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, it will return VedleggsListeDto which contains list of attachments to the specified application."
	)])
	@GetMapping("/soknad/{innsendingsId}/vedlegg")
	fun hentVedleggsListe(@PathVariable innsendingsId: String): ResponseEntity<List<VedleggDto>> {
		logger.info("Kall for å vedleggene til søknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		val vedleggsListeDto = soknadDto.vedleggsListe
		logger.info("Hentet vedleggene til søknad $innsendingsId")
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
		logger.info("Kall for å hente vedlegg $vedleggsId til søknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		val vedleggDto = soknadDto.vedleggsListe.firstOrNull { it.id.toString() == vedleggsId }
				?: throw ResourceNotFoundException("", "Ikke funnet vedlegg $vedleggsId for søknad $innsendingsId")
		logger.info("Hentet vedlegg $vedleggsId til søknad $innsendingsId")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(vedleggDto)
	}


	// Hvis det er et nytt vedlegg, så vil ikke frontend ha vedleggsid da dette settes av backend ved oppretting av resssurs.
	// Må derfor legge inn dummy id (f.eks. -1)?.
	@Operation(summary = "Requests adding attachment to a previously created application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the attachment is stored and an updated version of the VedleggDto is returned."
	)])
	@PostMapping("/soknad/{innsendingsId}/vedlegg")
	fun lagreVedlegg(
		@PathVariable innsendingsId: String,
		@RequestBody vedlegg: VedleggDto
	): ResponseEntity<VedleggDto> {
		logger.info("Kall for å lagre vedlegg til søknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		val vedleggDto = soknadService.lagreVedlegg(vedlegg, innsendingsId)
		logger.info("Lagret vedlegg ${vedleggDto.id} til søknad $innsendingsId")
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
		logger.info("Kall for å lagre fil på vedlegg $vedleggsId til søknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId eksisterer ikke for søknad $innsendingsId")

		// Ved opplasting av fil skal den valideres (f.eks. lovlig format, summen av størrelsen på filene på et vedlegg må være innenfor max størrelse).
		Validerer().validereFilformat(listOf(file.bytes))
		// Alle opplastede filer skal lagres som flatede (dvs. ikke skrivbar PDF) PDFer.
		val fil = KonverterTilPdf().tilPdf(file.bytes)
		val vedleggsFiler = soknadService.hentFiler(soknadDto, innsendingsId, vedleggsId, false, false)
		val opplastetFilStorrelse: Int = vedleggsFiler.filter {it.storrelse != null }.sumOf { it.storrelse!! }
		Validerer().validerStorrelse(opplastetFilStorrelse + fil.size, restConfig.maxFileSize )

		// Lagre
		val lagretFilDto = soknadService.lagreFil(soknadDto, FilDto(null, vedleggsId, file.originalFilename ?:"", "application/pdf", fil.size, fil, LocalDateTime.now()))

		logger.info("Lagret fil ${lagretFilDto.id} på vedlegg $vedleggsId til søknad $innsendingsId")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(FilIdDto(lagretFilDto.id))
	}

	// Søker skal kunne laste opp ett eller flere filer på ett vedlegg. Dette endepunktet tillater henting av en allerede opplastet fil.
	@Operation(summary = "Requests fetching a specific file uploaded to an attachment.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, the specified file is returned."
	)])
	@GetMapping("/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil/{filId}")
	fun hentFil(
		@PathVariable innsendingsId: String,
		@PathVariable vedleggsId: Long,
		@PathVariable filId: Long
	): ResponseEntity<ByteArray> {
		logger.info("Kall for å hente fil $filId på vedlegg $vedleggsId til søknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)

		val filDto = soknadService.hentFil(soknadDto, vedleggsId, filId)
		logger.info("Hentet fil ${filDto.id} på vedlegg $vedleggsId til søknad $innsendingsId")
		return ResponseEntity
			.status(HttpStatus.OK)
			.contentType(MediaType.APPLICATION_PDF)
			.contentLength(filDto.data?.size?.toLong()!!)
			.body(filDto.data)
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
		logger.info("Kall for å hente filinfo til vedlegg $vedleggsId til søknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		val filDtoListe = soknadService.hentFiler(soknadDto, innsendingsId, vedleggsId)
		logger.info("Hentet informasjon om opplastede filer på vedlegg $vedleggsId til søknad $innsendingsId")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(filDtoListe)
	}

	@Operation(summary = "Requests get on a previously uploaded file to an attachment.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
		description = "If successful, the file is removed from the attachment."
	)])
	@DeleteMapping("/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil/{filId}")
	fun slettFil(
		@PathVariable innsendingsId: String,
		@PathVariable vedleggsId: Long,
		@PathVariable filId: Long
	): ResponseEntity<BodyStatusResponseDto> {
		logger.info("Kall for å slette fil $filId på vedlegg $vedleggsId til søknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)

		soknadService.slettFil(soknadDto, vedleggsId, filId)
		logger.info("Slette fil $filId på vedlegg $vedleggsId til søknad $innsendingsId")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet fil med id $filId"))
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
		logger.info("Kall for å slette vedlegg $vedleggsId for søknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)

		soknadService.slettVedlegg(soknadDto, vedleggsId)
		logger.info("Slettet vedlegg $vedleggsId for søknad $innsendingsId")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet vedlegg med id $vedleggsId"))
	}

	@Operation(summary = "Requests delete of an application.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the application is deleted from the database and a confirmation string is returned."
	)])
	@DeleteMapping("/soknad/{innsendingsId}")
	fun slettSoknad(@PathVariable innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		logger.info("Kall for å slette søknad med id $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		soknadService.slettSoknadAvBruker(soknadDto)
		logger.info("Slettet søknad med id $innsendingsId")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id $innsendingsId"))
	}

	@Operation(summary = "Requests that the application shall be sent to NAV.", tags = ["operations"])
	@ApiResponses(value = [ApiResponse(responseCode = "200",
			description = "If successful, the application is sent to NAV, and a confirmation string is returned."
	)])
	@PostMapping("/sendInn/{innsendingsId}")
	fun sendInnSoknad(@PathVariable innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		logger.info("Kall for å sende inn soknad $innsendingsId")
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		soknadService.sendInnSoknad(soknadDto)
		logger.info("Sendt inn soknad $innsendingsId")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Soknad med id $innsendingsId er sendt inn til NAV"))
	}

}
