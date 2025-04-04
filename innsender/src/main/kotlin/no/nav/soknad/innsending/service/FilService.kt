package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.*
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants.TRANSACTION_TIMEOUT
import no.nav.soknad.innsending.util.dokumentsoknad.isLospost
import no.nav.soknad.innsending.util.mapping.*
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import no.nav.soknad.pdfutilities.PdfMerger
import no.nav.soknad.pdfutilities.Validerer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class FilService(
	private val repo: RepositoryUtils,
	private val exceptionHelper: ExceptionHelper,
	private val innsenderMetrics: InnsenderMetrics,
	private val vedleggService: VedleggService,
	private val pdfMerger: PdfMerger,
	private val restConfig: RestConfig,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Transactional(timeout= TRANSACTION_TIMEOUT)
	fun lagreFil(
		savedDokumentSoknadDto: DokumentSoknadDto,
		lagretVedleggDto: VedleggDto,
		innsendtVedleggDtos: List<VedleggDto>
	) {
		val matchInnsendtVedleggDto = innsendtVedleggDtos.firstOrNull {
			it.vedleggsnr == lagretVedleggDto.vedleggsnr
				&& it.mimetype == lagretVedleggDto.mimetype
				&& it.document?.isNotEmpty() ?: false
				&& it.erHoveddokument == lagretVedleggDto.erHoveddokument
				&& it.erVariant == lagretVedleggDto.erVariant
				&& it.formioId == lagretVedleggDto.formioId
		} ?: run {
			logger.error("Fant ikke matchende lagret vedlegg med innsendt vedlegg")
			throw BackendErrorException("Feil ved lagring av dokument ${lagretVedleggDto.tittel}. Fant ikke matchende lagret vedlegg ${lagretVedleggDto.tittel} med innsendt vedlegg, er variant = ${lagretVedleggDto.erVariant}")
		}

		// Finn eksisterende filer
		val eksisterendeVedleggsFiler =
			hentFiler(savedDokumentSoknadDto, savedDokumentSoknadDto.innsendingsId!!, lagretVedleggDto.id!!)
		val eksisterendeFil = eksisterendeVedleggsFiler.find { it.vedleggsid == lagretVedleggDto.id }

		// Lag ny fil eller oppdater eksisterende fil
		val filDto = lagFilDto(eksisterendeFil, matchInnsendtVedleggDto, lagretVedleggDto)
		lagreFil(savedDokumentSoknadDto, filDto)

	}

	private fun lagFilDto(
		eksisterendeFil: FilDto?,
		matchInnsendtVedleggDto: VedleggDto,
		lagretVedleggDto: VedleggDto
	) = FilDto(
		vedleggsid = lagretVedleggDto.id!!,
		id = eksisterendeFil?.id,
		filnavn = lagFilNavn(matchInnsendtVedleggDto),
		mimetype = lagretVedleggDto.mimetype,
		storrelse = matchInnsendtVedleggDto.document?.size,
		data = matchInnsendtVedleggDto.document,
		opprettetdato = OffsetDateTime.now()
	)

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun lagreFil(soknadDto: DokumentSoknadDto, filDto: FilDto): FilDto {
		val operation = InnsenderOperation.LAST_OPP.name

		if (!soknadDto.kanGjoreEndringer) {
			when (soknadDto.status.name) {
				SoknadsStatusDto.Innsendt.name -> throw IllegalActionException(
					message = "Søknad ${soknadDto.innsendingsId} er sendt inn og nye filer kan ikke lastes opp på denne. Opprett ny søknad for ettersendelse av informasjon. Innsendte søknader kan ikke endres. Ønsker søker å gjøre oppdateringer, så må vedkommende ettersende dette",
					errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
				)

				SoknadsStatusDto.SlettetAvBruker.name, SoknadsStatusDto.AutomatiskSlettet.name -> throw IllegalActionException(
					message = "Søknaden er slettet og ingen filer kan legges til. Søknader markert som slettet kan ikke endres. Søker må eventuelt opprette ny søknad",
					errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
				)

				else -> {
					throw BackendErrorException("Ukjent status ${soknadDto.status.name}. Lagring av filer på søknad med status ${soknadDto.status.name} er ikke håndtert")
				}
			}
		}

		val vedleggdto = soknadDto.vedleggsListe.firstOrNull { it.id == filDto.vedleggsid }
			?: throw ResourceNotFoundException("Vedlegg ${filDto.vedleggsid} til søknad ${soknadDto.innsendingsId} eksisterer ikke")

		val start = System.currentTimeMillis()
		logger.info("${soknadDto.innsendingsId!!}: Skal lagre fil med størrelse ${filDto.data?.size ?: 0} på vedlegg ${filDto.vedleggsid}")
		val savedFilDbData = try {
			repo.saveFilDbData(soknadDto.innsendingsId!!, mapTilFilDb(filDto))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			throw e
		}
		logger.info("${soknadDto.innsendingsId!!}: Lagret fil med størrelse ${filDto.data!!.size} på vedlegg ${filDto.vedleggsid} på ${System.currentTimeMillis()-start} ms")

		/* Skal bare validere størrelse på vedlegg som søker har lastet opp */
		if (soknadDto.vedleggsListe.any { it.id == filDto.vedleggsid && (soknadDto.isLospost() || !it.erHoveddokument) }) {
			logger.debug("${soknadDto.innsendingsId!!}: Valider størrelse av opplastinger på vedlegg ${filDto.vedleggsid} og søknad ${soknadDto.innsendingsId!!}")
			Validerer().validerStorrelse(
				soknadDto.innsendingsId!!,
				finnFilStorrelseSum(soknadDto, filDto.vedleggsid),
				0,
				restConfig.maxFileSize.toLong(),
				ErrorCode.VEDLEGG_FILE_SIZE_SUM_TOO_LARGE,
			)
			Validerer().validerStorrelse(
				soknadDto.innsendingsId!!,
				finnFilStorrelseSum(soknadDto),
				0,
				restConfig.maxFileSizeSum.toLong(),
				ErrorCode.FILE_SIZE_SUM_TOO_LARGE
			)
		}
		if (repo.hentVedlegg(filDto.vedleggsid).status != OpplastingsStatus.LASTET_OPP) {
			repo.updateVedleggStatus(
				innsendingsId = soknadDto.innsendingsId!!,
				vedleggsId = filDto.vedleggsid,
				opplastingsStatus = OpplastingsStatus.LASTET_OPP
			)
		}
		innsenderMetrics.incOperationsCounter(operation, soknadDto.tema)
		return lagFilDto(savedFilDbData, false)
	}

	fun filExtension(mimetype: Mimetype?): String =
		when (mimetype) {
			Mimetype.imageSlashPng -> ".png"
			Mimetype.imageSlashJpeg -> ".jpeg"
			Mimetype.applicationSlashJson -> ".json"
			Mimetype.applicationSlashPdf -> ".pdf"
			else -> ""
		}

	fun lagFilNavn(vedleggDto: VedleggDto): String {
		val ext = filExtension(vedleggDto.mimetype)
		return (vedleggDto.vedleggsnr + ext)
	}

	fun hentFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long): FilDto {
		val operation = InnsenderOperation.LAST_NED.name

		// Sjekk om vedlegget eksisterer
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException("Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke")

		try {
			val filDbData = repo.hentFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)
			innsenderMetrics.incOperationsCounter(operation, soknadDto.tema)
			return lagFilDto(filDbData)
		} catch (e: ResourceNotFoundException) {
			when (soknadDto.status) {
				SoknadsStatusDto.Innsendt -> throw IllegalActionException(
					message = "Søknad ${soknadDto.innsendingsId} er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til Ditt Nav og søk opp dine saker der. Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					cause = e,
					errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
				)

				SoknadsStatusDto.SlettetAvBruker, SoknadsStatusDto.AutomatiskSlettet -> throw IllegalActionException(
					message = "Søknaden er slettet og ingen filer er tilgjengelig. Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					cause = e,
					errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
				)

				else -> throw e
			}
		}
	}

	fun hentFiler(
		soknadDto: DokumentSoknadDto,
		innsendingsId: String,
		vedleggsId: Long,
		medFil: Boolean = false
	): List<FilDto> {
		return hentFiler(soknadDto, innsendingsId, vedleggsId, medFil, false)
	}

	fun hentFiler(
		soknadDto: DokumentSoknadDto,
		innsendingsId: String,
		vedleggsId: Long,
		medFil: Boolean = false,
		kastFeilNarNull: Boolean = false
	): List<FilDto> {
		// Sjekk om vedlegget eksisterer for soknad
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException("Vedlegg $vedleggsId til søknad $innsendingsId eksisterer ikke")

		val filDbDataList = if (medFil)
			repo.hentFilerTilVedlegg(innsendingsId, vedleggsId)
		else
			repo.hentFilerTilVedleggUtenFilData(innsendingsId, vedleggsId)

		if (filDbDataList.isEmpty() && kastFeilNarNull)
			when (soknadDto.status) {
				SoknadsStatusDto.Innsendt -> throw IllegalActionException(
					message = "Søknad $innsendingsId er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til https://www.nav.no/minside og søk opp dine saker der. Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
				)

				SoknadsStatusDto.SlettetAvBruker, SoknadsStatusDto.AutomatiskSlettet -> throw IllegalActionException(
					message = "Søknaden er slettet og ingen filer er tilgjengelig. Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
				)

				else -> {
					throw ResourceNotFoundException("Ingen filer funnet for oppgitt vedlegg $vedleggsId til søknad $innsendingsId")
				}
			}

		return filDbDataList.map { lagFilDto(it, medFil) }
	}

	fun finnFilStorrelseSum(soknadDto: DokumentSoknadDto, vedleggsId: Long): Long {
		return repo.hentSumFilstorrelseTilVedlegg(soknadDto.innsendingsId!!, vedleggsId)
	}

	fun finnFilStorrelseSum(soknadDto: DokumentSoknadDto, vedleggListe: List<VedleggDto> = soknadDto.vedleggsListe) =
		vedleggListe
			.filter { soknadDto.isLospost() || !it.erHoveddokument }
			.filter { it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt || it.opplastingsStatus == OpplastingsStatusDto.LastetOpp }
			.sumOf { repo.hentSumFilstorrelseTilVedlegg(soknadDto.innsendingsId!!, it.id!!) }

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun slettFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long): VedleggDto {
		val operation = InnsenderOperation.SLETT_FIL.name

		// Sjekk om vedlegget eksisterer
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException("Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke")

		repo.slettFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)
		logger.info("${soknadDto.innsendingsId}: Slettet fil $filId på vedlegg $vedleggsId")

		if (repo.countFiles(soknadDto.innsendingsId!!, vedleggsId) == 0) {
			val vedleggDto = soknadDto.vedleggsListe.first { it.id == vedleggsId }
			val nyOpplastingsStatus =
				if (vedleggDto.innsendtdato != null) OpplastingsStatus.INNSENDT else OpplastingsStatus.IKKE_VALGT
			logger.info("${soknadDto.innsendingsId}: Skal oppdatere vedleggsstatus $nyOpplastingsStatus til vedlegg ${vedleggsId}")
			repo.lagreVedlegg(
				mapTilVedleggDb(
					vedleggDto,
					soknadsId = soknadDto.id!!,
					vedleggDto.skjemaurl,
					opplastingsStatus = nyOpplastingsStatus
				)
			)
		}
		logger.info("${soknadDto.innsendingsId}: Skal hente vedlegg $vedleggsId med oppdatert status")
		val vedleggDto = vedleggService.hentVedleggDto(vedleggsId)
		innsenderMetrics.incOperationsCounter(operation, soknadDto.tema)
		return vedleggDto
	}

	fun slettfilerTilInnsendteSoknader(dagerGamle: Int) {
		logger.info("Slett alle opplastede filer for innsendte søknader mellom ${100 + dagerGamle} til $dagerGamle dager siden")
		repo.deleteAllBySoknadStatusAndInnsendtdato(dagerGamle)
	}

	// Beholder inntil avklaring rundt om vi automatisk skal slette eventuelle opplastede filer på vedlegg hvis søker spesifiserer send-senere eller sendes-av-andre
	private fun slettFilerDersomStatusUlikLastetOpp(
		patchVedleggDto: PatchVedleggDto,
		soknadDto: DokumentSoknadDto,
		vedleggsId: Long
	) {
		if (patchVedleggDto.opplastingsStatus != null && patchVedleggDto.opplastingsStatus != OpplastingsStatusDto.LastetOpp
			&& repo.findAllByVedleggsid(soknadDto.innsendingsId!!, vedleggsId).isNotEmpty()
		) {
			repo.slettFilerForVedlegg(vedleggsId)
			logger.info("Slettet filer til vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} da status er endret til ${patchVedleggDto.opplastingsStatus}")
		}
	}


	// For alle vedlegg til søknaden:
	// Hoveddokument kan ha ulike varianter. Hver enkelt av disse sendes inn som ulike vedlegg.
	// Bruker kan ha lastet opp flere filer for øvrige vedlegg. Disse må merges og sendes som en fil.
	fun ferdigstillVedleggsFiler(soknadDto: DokumentSoknadDto): List<VedleggDto> {
		return soknadDto.vedleggsListe.map {
			lagVedleggDtoMedOpplastetFil(
				hentOgMergeVedleggsFiler(
					soknadDto,
					soknadDto.innsendingsId!!,
					it
				), it
			)
		}.sortedByDescending { it.erHoveddokument } // Hoveddokument first
			.onEach {
				if (!it.erHoveddokument) logger.info("${soknadDto.innsendingsId}: Vedlegg ${it.vedleggsnr} har opplastet fil=${it.document != null} og erPakrevd=${it.erPakrevd}")
			}
	}

	fun hentOgMergeVedleggsFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggDto: VedleggDto): FilDto? {
		val filer = hentFiler(
			soknadDto,
			innsendingsId,
			vedleggDto.id!!,
			medFil = false,
			kastFeilNarNull = false
		).filter { it.storrelse != null && it.storrelse!! > 0 }
		if (filer.isEmpty()) return null

		return FilDto(
			vedleggsid = vedleggDto.id!!,
			id = null,
			filnavn = vedleggDto.vedleggsnr!!,
			mimetype = filer[0].mimetype,
			storrelse = filer.sumOf { it.storrelse ?: 0 },
			data = null,
			opprettetdato = filer[0].opprettetdato
		)
	}

	fun hentOgMergeVedleggsFiler(innsendingsId: String, vedleggDbList: List<VedleggDbData>): List<VedleggDto> {

		val vedleggDtos = mutableListOf<VedleggDto>()
		logger.info("$innsendingsId: HentOgMerge for ${vedleggDbList.map { it.uuid }}")
		vedleggDbList.forEach { it ->
			val filer = repo.hentFilerTilVedlegg(innsendingsId, it.id!!).filterNot { it.data == null }
			val vedleggsFil =
				if (filer.isEmpty()) {
					logger.info("$innsendingsId: HentOgMerge vedlegg ${it.uuid} har ikke opplastede filer")
					null
				} else if (filer[0].mimetype == Mimetype.applicationSlashPdf.value) {
					logger.info("$innsendingsId: HentOgMerge skal merge ${filer.size} PDFer til vedlegg ${it.uuid}")
					if (filer.all { fil -> fil.data == null }) {
						logger.warn("$innsendingsId: HentOgMerge vedlegg ${it.uuid} mangler opplastet filer på alle filobjekter, returnerer null")
						null
					} else {
						pdfMerger.mergePdfer(
							filer.filter { fil -> fil.data != null }.map { fil -> fil.data!! })
					}
				} else {
					if (filer.size > 1) {
						logger.warn("$innsendingsId: HentOgMerge vedlegg ${it.uuid} er ikke PDF og det er lastet opp ${filer.size}  filer på vedlegget, kan ikke merge returnerer kun første filen")
					}
					filer[0].data
				}
			logger.info("$innsendingsId: HentOgMerge størrelse ${vedleggsFil?.size} til vedlegg ${it.uuid}")
			vedleggDtos.add(lagVedleggDto(it, vedleggsFil))
		}
		return vedleggDtos
	}
}
