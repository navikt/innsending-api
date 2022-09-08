package no.nav.soknad.innsending.rest

import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FrontendApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.OpprettEttersendingGittInnsendingsId
import no.nav.soknad.innsending.model.OpprettEttersendingGittSkjemaNr
import no.nav.soknad.innsending.model.OpprettSoknadBody
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SafService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.ukjentEttersendingsId
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.MAX_AKTIVE_DAGER
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.pdfutilities.KonverterTilPdf
import no.nav.soknad.pdfutilities.Validerer
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import javax.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile

@RestController
@CrossOrigin(maxAge = 3600)
@Profile("test | dev | prod")
@ProtectedWithClaims(issuer = Constants.TOKENX, claimMap = [Constants.CLAIM_ACR_LEVEL_4])
class FrontEndRestApi(
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
	private val restConfig: RestConfig,
	private val innsenderMetrics: InnsenderMetrics,
	private val safService: SafService): FrontendApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@ApiOperation(
		value = "Kall for å opprette en søknad basert på skjemanummer og med en eventuell liste av vedlegg.",
		nickname = "opprettSoknad",
		notes = "På basis av oppgitt skjemanummer og eventuelle vedlegg, blir det opprettet en søknad som inviterer søker til å laste ned skjema for utfylling og opplasting av dette og eventuelle vedlegg.",
		response = DokumentSoknadDto::class)
	@ApiResponses(
		value = [ApiResponse(code = 201, message = "Created", response = DokumentSoknadDto::class)])
	@RequestMapping(
		method = [RequestMethod.POST],
		value = ["/frontend/v1/soknad"],
		produces = ["application/json"],
		consumes = ["application/json"]
	)
	override fun opprettSoknad(
		@ApiParam(
			required = true,
			value = "Data neccessary in order to publish a new task or message user notification."
		) @Valid @RequestBody opprettSoknadBody: OpprettSoknadBody
	): ResponseEntity<DokumentSoknadDto> {
		logger.info("Kall for å opprette søknad på skjema ${opprettSoknadBody.skjemanr}")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.OPPRETT.name)
		try {
			val brukerId = tilgangskontroll.hentBrukerFraToken()

			val dokumentSoknadDto = soknadService.opprettSoknad(
				brukerId,
				opprettSoknadBody.skjemanr,
				finnSpraakFraInput(opprettSoknadBody.sprak),
				opprettSoknadBody.vedleggsListe ?: emptyList()
			)
			logger.info("${dokumentSoknadDto.innsendingsId}: Opprettet søknad på skjema ${opprettSoknadBody.skjemanr}")
			return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(dokumentSoknadDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å opprette en ettersendingssøknad basert på innsendingsid til søknaden det skal ettersendes på.",
		nickname = "ettersendingPaInnsendingsId",
		notes = "På basis av oppgitt innsendingsid, blir det opprettet en ettersendingssøknad som inviterer søker til å laste opp vedlegg.",
		response = DokumentSoknadDto::class)
	@ApiResponses(
		value = [ApiResponse(code = 201, message = "Created", response = DokumentSoknadDto::class)])
	@RequestMapping(
		method = [RequestMethod.POST],
		value = ["/frontend/v1/ettersendingPaInnsendingsId"],
		produces = ["application/json"],
		consumes = ["application/json"]
	)
	override fun ettersendingPaInnsendingsId(
		@ApiParam(
			required = true,
			value = "Data neccessary in order to publish a new task or message user notification."
		) @Valid @RequestBody opprettEttersendingGittInnsendingsId: OpprettEttersendingGittInnsendingsId
	): ResponseEntity<DokumentSoknadDto> {
		logger.info("Kall for å opprette ettersending på søknad ${opprettEttersendingGittInnsendingsId.ettersendingTilinnsendingsId}")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.OPPRETT.name)
		try {
			val origSoknad = soknadService.hentSoknad(opprettEttersendingGittInnsendingsId.ettersendingTilinnsendingsId)
			val brukerId = tilgangskontroll.hentBrukerFraToken()
			tilgangskontroll.harTilgang(origSoknad, brukerId)

			val dokumentSoknadDto =
				soknadService.opprettSoknadForettersendingAvVedlegg(brukerId, opprettEttersendingGittInnsendingsId.ettersendingTilinnsendingsId)
			logger.info("${dokumentSoknadDto.innsendingsId}: Opprettet ettersending for innsendingsid ${opprettEttersendingGittInnsendingsId.ettersendingTilinnsendingsId}")
			return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(dokumentSoknadDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å opprette en ettersendingssøknad basert på et skjemanummer.",
		nickname = "opprettEttersendingGittSkjemanr",
		notes = "På basis av oppgitt skjemanummer, blir det opprettet en ettersendingssøknad som inviterer søker til å laste opp vedlegg.",
		response = DokumentSoknadDto::class)
	@ApiResponses(
		value = [ApiResponse(code = 201, message = "Created", response = DokumentSoknadDto::class)])
	@RequestMapping(
		method = [RequestMethod.POST],
		value = ["/frontend/v1/ettersendPaSkjema"],
		produces = ["application/json"],
		consumes = ["application/json"]
	)
	override fun opprettEttersendingGittSkjemanr(
		@ApiParam(
			required = true,
			value = "Data neccessary in order to publish a new task or message user notification."
		) @Valid @RequestBody opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr
	): ResponseEntity<DokumentSoknadDto> {
		logger.info("Kall for å opprette ettersending på skjema ${opprettEttersendingGittSkjemaNr.skjemanr}")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.OPPRETT.name)
		try {
			val brukerId = tilgangskontroll.hentBrukerFraToken()

			val arkiverteSoknader = safService.hentInnsendteSoknader(brukerId)
				.filter { opprettEttersendingGittSkjemaNr.skjemanr == it.skjemanr && it.innsendingsId != null }
				.filter { it.innsendtDato.isAfter(OffsetDateTime.now().minusDays(MAX_AKTIVE_DAGER)) }
				.sortedByDescending { it.innsendtDato }
				.toList()
			val innsendteSoknader =
				try {
					soknadService.hentInnsendteSoknader(tilgangskontroll.hentPersonIdents())
						.filter{it.skjemanr == opprettEttersendingGittSkjemaNr.skjemanr}
						.filter{it.innsendtDato!!.isAfter(OffsetDateTime.now().minusDays(MAX_AKTIVE_DAGER))}
						.sortedByDescending { it.innsendtDato }
						.toList()
				} catch (e: Exception) {
					logger.info("Ingen søknader funnet i basen for bruker på skjemanr = ${opprettEttersendingGittSkjemaNr.skjemanr}")
					emptyList()
				}

			logger.info("Gitt skjemaNr ${opprettEttersendingGittSkjemaNr.skjemanr}: Antall innsendteSoknader=${innsendteSoknader.size} og Antall arkiverteSoknader=${arkiverteSoknader.size}")
			val dokumentSoknadDto =
				opprettDokumentSoknadDto(innsendteSoknader, arkiverteSoknader, brukerId, opprettEttersendingGittSkjemaNr)

			logger.info("${dokumentSoknadDto.innsendingsId}: Opprettet ettersending på skjema ${opprettEttersendingGittSkjemaNr.skjemanr}")
			return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(dokumentSoknadDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	private fun opprettDokumentSoknadDto(
		innsendteSoknader: List<DokumentSoknadDto>,
		arkiverteSoknader: List<AktivSakDto>,
		brukerId: String,
		opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr
	): DokumentSoknadDto  =
			if (innsendteSoknader.isNotEmpty()) {
				if (arkiverteSoknader.isNotEmpty()) {
					if (innsendteSoknader[0].innsendingsId == arkiverteSoknader[0].innsendingsId ||
						innsendteSoknader[0].innsendtDato!!.isAfter(arkiverteSoknader[0].innsendtDato)
					) {
						soknadService.opprettSoknadForettersendingAvVedlegg(
							brukerId,
							if (innsendteSoknader[0].ettersendingsId != null && innsendteSoknader[0].ettersendingsId != ukjentEttersendingsId)
								innsendteSoknader[0].ettersendingsId!!
							else innsendteSoknader[0].innsendingsId!!
						)
					} else {
						// Det er blitt sendt inn en søknad en annen vei til arkivet, knytt ettersendingen til denne ved å liste innsendte dokumenter
						// Opprett en ettersendingssøknad med innsendte vedlegg fra arkiverteSoknader[0]+ eventuelle ekstra vedlegg fra input.
						soknadService.opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(
							brukerId,
							arkiverteSoknader[0],
							finnSpraakFraInput(opprettEttersendingGittSkjemaNr.sprak),
							opprettEttersendingGittSkjemaNr.vedleggsListe ?: emptyList()
						)
					}
				} else {
					soknadService.opprettSoknadForettersendingAvVedlegg(
						brukerId,
						if (innsendteSoknader[0].ettersendingsId != null && innsendteSoknader[0].ettersendingsId != ukjentEttersendingsId)
							innsendteSoknader[0].ettersendingsId!!
						else innsendteSoknader[0].innsendingsId!!
					)
				}
			} else if (arkiverteSoknader.isNotEmpty()) {
				// Det er blitt sendt inn en søknad en annen vei til arkivet, knytt ettersendingen til denne ved å liste innsendte dokumenter
				// Opprett en ettersendingssøknad med innsendte vedlegg fra arkiverteSoknader[0]+ eventuelle ekstra vedlegg fra input.
				soknadService.opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(
					brukerId,
					arkiverteSoknader[0],
					finnSpraakFraInput(opprettEttersendingGittSkjemaNr.sprak),
					opprettEttersendingGittSkjemaNr.vedleggsListe ?: emptyList()
				)
			} else {
				soknadService.opprettSoknadForEttersendingGittSkjemanr(
					brukerId,
					opprettEttersendingGittSkjemaNr.skjemanr,
					finnSpraakFraInput(opprettEttersendingGittSkjemaNr.sprak),
					opprettEttersendingGittSkjemaNr.vedleggsListe ?: emptyList()
				)
			}

	@ApiOperation(
		value = "Kall for å hente alle opprettede ikke innsendte søknader.",
		nickname = "hentAktiveOpprettedeSoknader",
		notes = "Det returneres en liste av alle søknader som søker ikke har sendt inn.",
		response = DokumentSoknadDto::class,
		responseContainer = "List")
	@ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Successful operation",
			response = DokumentSoknadDto::class,
			responseContainer = "List"
		)])
	@RequestMapping(
		method = [RequestMethod.GET],
		value = ["/frontend/v1/soknad"],
		produces = ["application/json"]
	)
	override fun hentAktiveOpprettedeSoknader(): ResponseEntity<List<DokumentSoknadDto>> {
		logger.info("Kall for å hente alle opprette ikke innsendte søknader")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.HENT.name)
		try {
			val brukerIds = tilgangskontroll.hentPersonIdents()
			val dokumentSoknadDtos = soknadService.hentAktiveSoknader(brukerIds)
			logger.info("Hentet ${dokumentSoknadDtos.size} søknader opprettet av bruker")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(dokumentSoknadDtos)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å hente opprettet søknad gitt innsendingsId.",
		nickname = "hentSoknad",
		notes = "Dersom funnet, returneres søknaden.",
		response = DokumentSoknadDto::class)
	@ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Successful operation",
			response = DokumentSoknadDto::class
		)])
	@RequestMapping(
		method = [RequestMethod.GET],
		value = ["/frontend/v1/soknad/{innsendingsId}"],
		produces = ["application/json"]
	)
	override fun hentSoknad(@PathVariable innsendingsId: String): ResponseEntity<DokumentSoknadDto> {
		logger.info("$innsendingsId: Kall for å hente søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.HENT.name)
		try {
			val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(dokumentSoknadDto)
			logger.info("$innsendingsId: Hentet søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(dokumentSoknadDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å endre visningsSteg på søknad gitt innsendingsId.",
		nickname = "endreSoknad",
		notes = "Dersom endring er vellykket, returneres 204.")
	@ApiResponses(
		value = [ApiResponse(code = 204, message = "No content")])
	@RequestMapping(
		method = [RequestMethod.PATCH],
		value = ["/frontend/v1/soknad/{innsendingsId}"],
		consumes = ["application/json"]
	)
	override fun endreSoknad(@ApiParam(value = "identifisering av søknad som skal oppdateres", required=true) @PathVariable("innsendingsId") innsendingsId: String
									,@ApiParam(value = "New value for visningsSteg." ,required=true ) @Valid @RequestBody patchSoknadDto: PatchSoknadDto
	): ResponseEntity<Unit> {
		logger.info("$innsendingsId: Kall for å endre søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.ENDRE.name)
		try {
			val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(dokumentSoknadDto)
			soknadService.endreSoknad(dokumentSoknadDto.id!!, patchSoknadDto.visningsSteg)
			logger.info("$innsendingsId: Oppdatert søknad")
			return ResponseEntity(HttpStatus.NO_CONTENT)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å hente vedlegg (inkludert hoveddokument) til søknad gitt innsendingsId.",
		nickname = "hentVedleggsListe",
		notes = "Dersom funnet, returneres liste av søknadens vedlegg.",
		response = VedleggDto::class,
		responseContainer = "List")
	@ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Successful operation",
			response = VedleggDto::class,
			responseContainer = "List"
		)])
	@RequestMapping(
		method = [RequestMethod.GET],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg"],
		produces = ["application/json"]
	)
	override fun hentVedleggsListe(@PathVariable innsendingsId: String): ResponseEntity<List<VedleggDto>> {
		logger.info("$innsendingsId: Kall for å vedleggene til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.HENT.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			val vedleggsListeDto = soknadDto.vedleggsListe
			logger.info("$innsendingsId: Hentet vedleggene til søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(vedleggsListeDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å hente ett spesifikt vedlegg med dens  til søknad gitt innsendingsId.",
		nickname = "hentVedlegg",
		notes = "Dersom funnet, returneres spesifisert vedlegg.",
		response = VedleggDto::class)
	@ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Successful operation",
			response = VedleggDto::class
		)])
	@RequestMapping(
		method = [RequestMethod.GET],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg/{vedleggsId}"],
		produces = ["application/json"]
	)
	override fun hentVedlegg(
		@ApiParam(required = true, value = "identifisering av søknad som skal hentes") @PathVariable(
			value = "innsendingsId"
		) innsendingsId: String,
		@ApiParam(
			required = true,
			value = "identifisering av vedlegg som skal hentes"
		) @PathVariable(value = "vedleggsId") vedleggsId: Long
	): ResponseEntity<VedleggDto> {
		logger.info("$innsendingsId: Kall for å hente vedlegg $vedleggsId til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.HENT.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			val vedleggDto = soknadDto.vedleggsListe.firstOrNull { it.id == vedleggsId }
					?: throw ResourceNotFoundException("", "Ikke funnet vedlegg $vedleggsId for søknad $innsendingsId")
			logger.info("$innsendingsId: Hentet vedlegg $vedleggsId til søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(vedleggDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å endre tittel og status på et vedlegg.",
		nickname = "endreVedlegg",
		notes = "Kall for å endre tittel og status på et vedlegg. Merk at tittel kun kan endres på vedlegg av type Annet (vedleggsid=N6).",
		response = VedleggDto::class)
	@ApiResponses(
		value = [ApiResponse(code = 200, message = "Successful operation", response = VedleggDto::class)])
	@RequestMapping(
		method = [RequestMethod.PATCH],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg/{vedleggsId}"],
		produces = ["application/json"],
		consumes = ["application/json"]
	)
	override fun endreVedlegg(@ApiParam(value = "identifisering av søknad hvis vedlegg skal endres", required=true) @PathVariable("innsendingsId") innsendingsId: String
									 ,@ApiParam(value = "identifisering av vedlegg som skal endres", required=true) @PathVariable("vedleggsId") vedleggsId: Long
									 ,@ApiParam(value = "Data som skal endres" ,required=true ) @Valid @RequestBody patchVedleggDto: PatchVedleggDto
	): ResponseEntity<VedleggDto> {
		logger.info("$innsendingsId: Kall for å endre vedlegg til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.ENDRE.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			val vedleggDto = soknadService.endreVedlegg(patchVedleggDto, vedleggsId, soknadDto)
			logger.info("$innsendingsId: Lagret vedlegg ${vedleggDto.id} til søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(vedleggDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}


	@ApiOperation(
		value = "Kall for å legge til et nytt vedlegg til søknad gitt innsendingsId.",
		nickname = "lagreVedlegg",
		notes = "Det er kun vedlegg av type Annet( vedleggsnr=N6) som søker kan legge til søknaden. Hvis vellykket vedlegget med id returneres.",
		response = VedleggDto::class)
	@ApiResponses(
		value = [ApiResponse(code = 201, message = "Created", response = VedleggDto::class)])
	@RequestMapping(
		method = [RequestMethod.POST],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg"],
		produces = ["application/json"],
		consumes = ["application/json"]
	)
	override fun lagreVedlegg(
		@PathVariable innsendingsId: String, postVedleggDto: PostVedleggDto?
	): ResponseEntity<VedleggDto> {
		logger.info("$innsendingsId: Kall for å lagre vedlegg til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.LAST_OPP.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			val vedleggDto = soknadService.leggTilVedlegg(soknadDto, postVedleggDto?.tittel)
			logger.info("$innsendingsId: Lagret vedlegg ${vedleggDto.id} til søknad")
			return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(vedleggDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	// Søker skal kunne laste opp ett eller flere filer på ett vedlegg. Dette endepunktet tillater opplasting av en fil.
	@ApiOperation(
		value = "Kall for å legge til en fil på ett spesifikt vedlegg til en søknad gitt innsendingsId og vedleggsId.",
		nickname = "lagreFil",
		notes = "Dersom funnet, returneres id til filen som er lagret.",
		response = FilDto::class)
	@ApiResponses(
		value = [ApiResponse(code = 201, message = "Created", response = FilDto::class)])
	@RequestMapping(
		method = [RequestMethod.POST],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil"],
		produces = ["application/json"],
		consumes = ["multipart/form-data"]
	)
	override fun lagreFil(
		@ApiParam(required = true, value = "identifisering av søknad som skal hentes") @PathVariable(
			value = "innsendingsId"
		) innsendingsId: String,
		@ApiParam(required = true, value = "identifisering av vedlegg som skal hentes") @PathVariable(
			value = "vedleggsId"
		) vedleggsId: Long,
		@ApiParam(value = "file detail") @Valid @RequestPart(value = "file") file: Resource
	): ResponseEntity<FilDto> {
		logger.info("$innsendingsId: Kall for å lagre fil på vedlegg $vedleggsId til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.LAST_OPP.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
				throw ResourceNotFoundException(null, "Vedlegg $vedleggsId eksisterer ikke for søknad $innsendingsId")

			// Ved opplasting av fil skal den valideres (f.eks. lovlig format, summen av størrelsen på filene på et vedlegg må være innenfor max størrelse).
			if (!file.isReadable) throw IllegalActionException("Ingen fil opplastet", "Opplasting feilet")
			val opplastet = (file as ByteArrayResource).byteArray
			Validerer().validereFilformat(listOf(opplastet))
			// Alle opplastede filer skal lagres som flatede (dvs. ikke skrivbar PDF) PDFer.
			val fil = KonverterTilPdf().tilPdf(opplastet)
			val vedleggsFiler = soknadService.hentFiler(soknadDto, innsendingsId, vedleggsId, false, false)
			val opplastetFilStorrelse: Int = vedleggsFiler.filter {it.storrelse != null }.sumOf { it.storrelse!! }
			Validerer().validerStorrelse(opplastetFilStorrelse + fil.size, restConfig.maxFileSize )

			// Lagre
			val lagretFilDto = soknadService.lagreFil(soknadDto, FilDto(vedleggsId, null, file.filename ?:"", Mimetype.applicationSlashPdf, fil.size, fil, OffsetDateTime.now()))

			logger.info("$innsendingsId: Lagret fil ${lagretFilDto.id} på vedlegg $vedleggsId til søknad")
			return ResponseEntity
				.status(HttpStatus.CREATED)
				.body(lagretFilDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	// Søker skal kunne laste opp ett eller flere filer på ett vedlegg. Dette endepunktet tillater henting av en allerede opplastet fil.
	@ApiOperation(
		value = "Kall for å hente en opplastet fil på ett spesifikt vedlegg til en søknad gitt innsendingsId, vedleggsId og filId.",
		nickname = "hentFil",
		notes = "Dersom funnet, returneres den lagrede file.",
		response = org.springframework.core.io.Resource::class)
	@ApiResponses(
		value = [ApiResponse(code = 200, message = "Successful operation", response = org.springframework.core.io.Resource::class)])
	@RequestMapping(
		method = [RequestMethod.GET],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil/{filId}"],
		produces = ["application/pdf"]
	)
	@CrossOrigin
	override fun hentFil(
		@ApiParam(
			required = true,
			value = "identifisering av søknad"
		) @PathVariable(value = "innsendingsId") innsendingsId: String,
		@ApiParam(
			required = true,
			value = "identifisering av vedlegg"
		) @PathVariable(value = "vedleggsId") vedleggsId: Long,
		@ApiParam(
			required = true,
			value = "identifisering av fil som skal hentes"
		) @PathVariable(value = "filId") filId: Long
	): ResponseEntity<Resource> {
		logger.info("$innsendingsId: Kall for å hente fil $filId på vedlegg $vedleggsId til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.LAST_NED.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)

			val filDto = soknadService.hentFil(soknadDto, vedleggsId, filId)
			logger.info("$innsendingsId: Hentet fil ${filDto.id} på vedlegg $vedleggsId til søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.contentType(MediaType.APPLICATION_PDF)
				.contentLength(filDto.data?.size?.toLong()!!)
				.body(mapTilResource(filDto))
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	private fun mapTilResource(filDto: FilDto): Resource {
		if (filDto.data == null) throw ResourceNotFoundException("Fant ikke fil", "Fant ikke angitt fil på ${filDto.id}")
		return ByteArrayResource(filDto.data!!)
	}

	@ApiOperation(
		value = "Kall for å hente en opplastede filer på ett vedlegg til en søknad gitt innsendingsId, vedleggsId og filId.",
		nickname = "hentFilInfoForVedlegg",
		notes = "Dersom funnet, returneres liste med informasjon om opplastede filer, hvis ingen filer lastet opp returneres en tom liste.",
		response = FilDto::class,
		responseContainer = "List")
	@ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Successful operation",
			response = FilDto::class,
			responseContainer = "List"
		)])
	@RequestMapping(
		method = [RequestMethod.GET],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil"],
		produces = ["application/json"]
	)
	override fun hentFilInfoForVedlegg(
		@PathVariable innsendingsId: String,
		@PathVariable vedleggsId: Long
	): ResponseEntity<List<FilDto>> {
		logger.info("$innsendingsId: Kall for å hente filinfo til vedlegg $vedleggsId til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.HENT.name)
		try {
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		val filDtoListe = soknadService.hentFiler(soknadDto, innsendingsId, vedleggsId)
		logger.info("$innsendingsId: Hentet informasjon om opplastede filer på vedlegg $vedleggsId til søknad")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(filDtoListe)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å slette en opplastet fil på en søknad gitt innsendingsId, vedleggsId og filId.",
		nickname = "slettFil",
		notes = "Dersom funnet, slettes filen.",
		response = BodyStatusResponseDto::class)
	@ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Successful operation",
			response = BodyStatusResponseDto::class
		)])
	@RequestMapping(
		method = [RequestMethod.DELETE],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg/{vedleggsId}/fil/{filId}"],
		produces = ["application/json"]
	)
	@CrossOrigin
	override fun slettFil(
		@PathVariable innsendingsId: String,
		@PathVariable vedleggsId: Long,
		@PathVariable filId: Long
	): ResponseEntity<BodyStatusResponseDto> {
		logger.info("Kall for å slette fil $filId på vedlegg $vedleggsId til søknad $innsendingsId")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.SLETT_FIL.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)

			soknadService.slettFil(soknadDto, vedleggsId, filId)
			logger.info("$innsendingsId: Slettet fil $filId på vedlegg $vedleggsId til søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet fil med id $filId"))
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å slette ett spesifikt vedlegg med dens filer til søknad gitt innsendingsId.",
		nickname = "slettVedlegg",
		notes = "Dersom funnet og vedleggstypen=N6 slettes vedlegget.",
		response = BodyStatusResponseDto::class)
	@ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Successful operation",
			response = BodyStatusResponseDto::class
		)])
	@RequestMapping(
		method = [RequestMethod.DELETE],
		value = ["/frontend/v1/soknad/{innsendingsId}/vedlegg/{vedleggsId}"],
		produces = ["application/json"]
	)
	@CrossOrigin
	override fun slettVedlegg(@PathVariable innsendingsId: String, @PathVariable vedleggsId: Long): ResponseEntity<BodyStatusResponseDto> {
		logger.info("$innsendingsId: Kall for å slette vedlegg $vedleggsId for søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.SLETT_FIL.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)

			soknadService.slettVedlegg(soknadDto, vedleggsId)
			logger.info("$innsendingsId: Slettet vedlegg $vedleggsId for søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet vedlegg med id $vedleggsId"))
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å slette opprettet søknad gitt innsendingsId.",
		nickname = "slettSoknad",
		notes = "Dersom funnet, slettes søknaden.",
		response = BodyStatusResponseDto::class)
	@ApiResponses(
		value = [io.swagger.annotations.ApiResponse(
			code = 200,
			message = "Successful operation",
			response = BodyStatusResponseDto::class
		)])
	@RequestMapping(
		method = [RequestMethod.DELETE],
		value = ["/frontend/v1/soknad/{innsendingsId}"],
		produces = ["application/json"]
	)
	@CrossOrigin
	override fun slettSoknad(@PathVariable innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		logger.info("$innsendingsId: Kall for å slette søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.SLETT.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			soknadService.slettSoknadAvBruker(soknadDto)
			logger.info("Slettet søknad med id $innsendingsId")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id $innsendingsId"))
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@ApiOperation(
		value = "Kall for å sende inn en søknad.",
		nickname = "sendInnSoknad",
		notes = "Dersom funnet, sendes metadat om søknaden og opplastede filer inn til NAV.",
		response = KvitteringsDto::class)
	@ApiResponses(
		value = [ApiResponse(code = 200, message = "Successful operation", response = KvitteringsDto::class)])
	@RequestMapping(
		method = [RequestMethod.POST],
		value = ["/frontend/v1/sendInn/{innsendingsId}"],
		produces = ["application/json"]
	)
	override fun sendInnSoknad(@PathVariable innsendingsId: String): ResponseEntity<KvitteringsDto> {
		logger.info("$innsendingsId: Kall for å sende inn soknad ")

		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.SEND_INN.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			val kvitteringsDto = soknadService.sendInnSoknad(soknadDto)
			logger.info("$innsendingsId: Sendt inn soknad.\n" +
				"InnsendteVedlegg=${kvitteringsDto.innsendteVedlegg?.size}, SkalEttersendes=${kvitteringsDto.skalEttersendes?.size}")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(kvitteringsDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

}
