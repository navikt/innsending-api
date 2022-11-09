package no.nav.soknad.innsending.rest

import no.nav.security.token.support.core.api.Unprotected
import no.nav.soknad.innsending.api.FrontendApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SafService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.ukjentEttersendingsId
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.pdfutilities.KonverterTilPdf
import no.nav.soknad.pdfutilities.Validerer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

//TODO slett når testing er i mål
@RestController
@CrossOrigin(maxAge = 3600)
@Profile("dev | docker | spring | default")
@Unprotected
@RequestMapping("/unprot")
class FrontEndRestAPILocalTest(
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
	private val restConfig: RestConfig,
	private val innsenderMetrics: InnsenderMetrics,
	private val safService: SafService
): FrontendApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun opprettSoknad(opprettSoknadBody: OpprettSoknadBody): ResponseEntity<DokumentSoknadDto> {
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

	override fun ettersendingPaInnsendingsId(opprettEttersendingGittInnsendingsId: OpprettEttersendingGittInnsendingsId): ResponseEntity<DokumentSoknadDto> {
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

	override fun opprettEttersendingGittSkjemanr(opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr): ResponseEntity<DokumentSoknadDto> {
		logger.info("Kall for å opprette ettersending på skjema ${opprettEttersendingGittSkjemaNr.skjemanr}")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.OPPRETT.name)
		try {
			val brukerId = tilgangskontroll.hentBrukerFraToken()

			val arkiverteSoknader = safService.hentInnsendteSoknader(brukerId)
				.filter { opprettEttersendingGittSkjemaNr.skjemanr == it.skjemanr && it.innsendingsId != null }
				.filter { it.innsendtDato.isAfter(OffsetDateTime.now().minusDays(Constants.MAX_AKTIVE_DAGER)) }
				.sortedByDescending { it.innsendtDato }
			val innsendteSoknader =
				try {
					soknadService.hentInnsendteSoknader(tilgangskontroll.hentPersonIdents())
						.filter{it.skjemanr == opprettEttersendingGittSkjemaNr.skjemanr}
						.filter{it.innsendtDato!!.isAfter(OffsetDateTime.now().minusDays(Constants.MAX_AKTIVE_DAGER))}
						.sortedByDescending { it.innsendtDato }
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
					soknadService.opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg(
						brukerId = brukerId,
						nyesteSoknad = innsendteSoknader[0],
						sprak = opprettEttersendingGittSkjemaNr.sprak,
						vedleggsnrListe = opprettEttersendingGittSkjemaNr.vedleggsListe ?: emptyList()
					)
				} else {
					// Det er blitt sendt inn en søknad en annen vei til arkivet, knytt ettersendingen til denne ved å liste innsendte dokumenter
					// Opprett en ettersendingssøknad med innsendte vedlegg fra arkiverteSoknader[0]+ eventuelle ekstra vedlegg fra input.
					soknadService.opprettSoknadForettersendingAvVedleggGittArkivertSoknadOgVedlegg(
						brukerId = brukerId,
						arkivertSoknad = arkiverteSoknader[0],
						sprak = opprettEttersendingGittSkjemaNr.sprak,
						opprettEttersendingGittSkjemaNr = opprettEttersendingGittSkjemaNr
					)
				}
			} else {
				soknadService.opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg(
					brukerId = brukerId,
					nyesteSoknad = innsendteSoknader[0],
					sprak = opprettEttersendingGittSkjemaNr.sprak,
					vedleggsnrListe = opprettEttersendingGittSkjemaNr.vedleggsListe ?: emptyList()
				)
			}
		} else if (arkiverteSoknader.isNotEmpty()) {
			// Det er blitt sendt inn en søknad en annen vei til arkivet, knytt ettersendingen til denne ved å liste innsendte dokumenter
			// Opprett en ettersendingssøknad med innsendte vedlegg fra arkiverteSoknader[0]+ eventuelle ekstra vedlegg fra input.
			soknadService.opprettSoknadForettersendingAvVedleggGittArkivertSoknadOgVedlegg(
				brukerId = brukerId,
				arkivertSoknad = arkiverteSoknader[0],
				sprak = opprettEttersendingGittSkjemaNr.sprak,
				opprettEttersendingGittSkjemaNr = opprettEttersendingGittSkjemaNr
			)
		} else {
			soknadService.opprettSoknadForEttersendingGittSkjemanr(
				brukerId,
				opprettEttersendingGittSkjemaNr.skjemanr,
				finnSpraakFraInput(opprettEttersendingGittSkjemaNr.sprak),
				opprettEttersendingGittSkjemaNr.vedleggsListe ?: emptyList()
			)
		}

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

	override fun hentSoknad(innsendingsId: String): ResponseEntity<DokumentSoknadDto> {
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

	override fun endreSoknad(innsendingsId: String, patchSoknadDto: PatchSoknadDto): ResponseEntity<Unit> {
		logger.info("$innsendingsId: Kall for å endre søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.ENDRE.name)
		try {
			val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(dokumentSoknadDto)
			if (dokumentSoknadDto.status != SoknadsStatusDto.opprettet) {
				throw IllegalActionException("Søknaden kan ikke vises",
					"Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
					"errorCode.illegalAction.applicationSentInOrDeleted")
			}
			soknadService.endreSoknad(dokumentSoknadDto.id!!, patchSoknadDto.visningsSteg)
			logger.info("$innsendingsId: Oppdatert søknad")
			return ResponseEntity(HttpStatus.NO_CONTENT)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	override fun hentVedleggsListe(innsendingsId: String): ResponseEntity<List<VedleggDto>> {
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

	override fun hentVedlegg(innsendingsId: String, vedleggsId: Long): ResponseEntity<VedleggDto> {
		logger.info("$innsendingsId: Kall for å hente vedlegg $vedleggsId til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.HENT.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			val vedleggDto = soknadDto.vedleggsListe.firstOrNull { it.id == vedleggsId }
				?: throw ResourceNotFoundException("", "Ikke funnet vedlegg $vedleggsId for søknad $innsendingsId", "errorCode.resourceNotFound.attachmentNotFound")
			logger.info("$innsendingsId: Hentet vedlegg $vedleggsId til søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(vedleggDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	override fun endreVedlegg(innsendingsId: String, vedleggsId: Long, patchVedleggDto: PatchVedleggDto): ResponseEntity<VedleggDto> {
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


	override fun lagreVedlegg(innsendingsId: String, postVedleggDto: PostVedleggDto?): ResponseEntity<VedleggDto> {
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
	override fun lagreFil(innsendingsId: String, vedleggsId: Long, file: Resource): ResponseEntity<FilDto> {
		logger.info("$innsendingsId: Kall for å lagre fil på vedlegg $vedleggsId til søknad")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.LAST_OPP.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)
			if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
				throw ResourceNotFoundException(null, "Vedlegg $vedleggsId eksisterer ikke for søknad $innsendingsId", "errorCode.resourceNotFound.attachmentNotFound")

			// Ved opplasting av fil skal den valideres (f.eks. lovlig format, summen av størrelsen på filene på et vedlegg må være innenfor max størrelse).
			if (!file.isReadable) throw IllegalActionException("Ingen fil opplastet", "Opplasting feilet", "errorCode.illegalAction.fileCannotBeRead")
			val opplastet = (file as ByteArrayResource).byteArray
			Validerer().validereFilformat(listOf(opplastet))
			// Alle opplastede filer skal lagres som flatede (dvs. ikke skrivbar PDF) PDFer.
			val fil = KonverterTilPdf().tilPdf(opplastet)
			val vedleggsFiler = soknadService.hentFiler(soknadDto, innsendingsId, vedleggsId, false, false)

			val opplastetPaVedlegg: Long = soknadService.finnFilStorrelseSum(soknadDto, vedleggsId)
			val opplastetPaSoknad: Long = soknadService.finnFilStorrelseSum(soknadDto)
			Validerer().validerStorrelse(opplastetPaVedlegg + fil.size, restConfig.maxFileSize.toLong(),"errorCode.illegalAction.vedleggFileSizeSumTooLarge" )
			Validerer().validerStorrelse(opplastetPaSoknad + fil.size, restConfig.maxFileSizeSum.toLong(),"errorCode.illegalAction.fileSizeSumTooLarge" )

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
	@CrossOrigin
	override fun hentFil(innsendingsId: String, vedleggsId: Long, filId: Long): ResponseEntity<Resource> {
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
		if (filDto.data == null) throw ResourceNotFoundException("Fant ikke fil", "Fant ikke angitt fil på ${filDto.id}", "errorCode.resourceNotFound.fileNotFound")
		return ByteArrayResource(filDto.data!!)
	}

	override fun hentFilInfoForVedlegg(innsendingsId: String, vedleggsId: Long): ResponseEntity<List<FilDto>> {
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

	@CrossOrigin
	override fun slettFil(innsendingsId: String, vedleggsId: Long, filId: Long): ResponseEntity<VedleggDto> {
		logger.info("Kall for å slette fil $filId på vedlegg $vedleggsId til søknad $innsendingsId")
		val histogramTimer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.SLETT_FIL.name)
		try {
			val soknadDto = soknadService.hentSoknad(innsendingsId)
			tilgangskontroll.harTilgang(soknadDto)

			val vedleggDto = soknadService.slettFil(soknadDto, vedleggsId, filId)
			logger.info("$innsendingsId: Slettet fil $filId på vedlegg $vedleggsId til søknad")
			return ResponseEntity
				.status(HttpStatus.OK)
				.body(vedleggDto)
		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(histogramTimer)
		}
	}

	@CrossOrigin
	override fun slettVedlegg(innsendingsId: String, vedleggsId: Long): ResponseEntity<BodyStatusResponseDto> {
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

	@CrossOrigin
	override fun slettSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
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
