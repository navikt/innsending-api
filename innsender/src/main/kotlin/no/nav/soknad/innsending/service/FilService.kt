package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.VedleggDbData
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import no.nav.soknad.pdfutilities.PdfMerger
import no.nav.soknad.pdfutilities.Validerer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class FilService(
	private val repo: RepositoryUtils,
	private val exceptionHelper: ExceptionHelper,
	private val innsenderMetrics: InnsenderMetrics,
	private val vedleggService: VedleggService,
	private val restConfig: RestConfig,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun lagreFil(
		savedDokumentSoknadDto: DokumentSoknadDto,
		lagretVedleggDto: VedleggDto,
		innsendtVedleggDtos: List<VedleggDto>
	) {
		val matchInnsendtVedleggDto = innsendtVedleggDtos.firstOrNull {
			it.vedleggsnr == lagretVedleggDto.vedleggsnr && it.mimetype == lagretVedleggDto.mimetype && it.document?.isNotEmpty() ?: false
				&& it.erHoveddokument == lagretVedleggDto.erHoveddokument && it.erVariant == lagretVedleggDto.erVariant
		}

		if (matchInnsendtVedleggDto != null) {
			val filDto = FilDto(
				lagretVedleggDto.id!!, null, lagFilNavn(matchInnsendtVedleggDto), lagretVedleggDto.mimetype!!,
				matchInnsendtVedleggDto.document?.size, matchInnsendtVedleggDto.document, OffsetDateTime.now()
			)
			lagreFil(savedDokumentSoknadDto, filDto)
			return
		}
		logger.error("Fant ikke matchende lagret vedlegg med innsendt vedlegg")
		throw BackendErrorException(
			"Fant ikke matchende lagret vedlegg ${lagretVedleggDto.tittel} med innsendt vedlegg, er variant = ${lagretVedleggDto.erVariant}",
			"Feil ved lagring av dokument ${lagretVedleggDto.tittel}, prøv igjen",
			"errorCode.backendError.fileInconsistencyError"
		)
	}

	@Transactional
	fun lagreFil(soknadDto: DokumentSoknadDto, filDto: FilDto): FilDto {
		val operation = InnsenderOperation.LAST_OPP.name

		if (!soknadDto.kanGjoreEndringer) {
			when (soknadDto.status.name) {
				SoknadsStatusDto.innsendt.name -> throw IllegalActionException(
					"Innsendte søknader kan ikke endres. Ønsker søker å gjøre oppdateringer, så må vedkommende ettersende dette",
					"Søknad ${soknadDto.innsendingsId} er sendt inn og nye filer kan ikke lastes opp på denne. Opprett ny søknad for ettersendelse av informasjon",
					"errorCode.illegalAction.applicationSentInOrDeleted"
				)

				SoknadsStatusDto.slettetAvBruker.name, SoknadsStatusDto.automatiskSlettet.name -> throw IllegalActionException(
					"Søknader markert som slettet kan ikke endres. Søker må eventuelt opprette ny søknad",
					"Søknaden er slettet og ingen filer kan legges til",
					"errorCode.illegalAction.applicationSentInOrDeleted"
				)

				else -> {
					throw BackendErrorException(
						"Ukjent status ${soknadDto.status.name}",
						"Lagring av filer på søknad med status ${soknadDto.status.name} er ikke håndtert",
						"errorCode.backendError.fileSaveError"
					)
				}
			}
		}

		if (soknadDto.vedleggsListe.none { it.id == filDto.vedleggsid })
			throw ResourceNotFoundException(
				null,
				"Vedlegg $filDto.vedleggsid til søknad ${soknadDto.innsendingsId} eksisterer ikke",
				"errorCode.resourceNotFound.attachmentNotFound"
			)

		logger.debug("${soknadDto.innsendingsId!!}: Skal lagre fil med størrelse ${filDto.data!!.size} på vedlegg ${filDto.vedleggsid}")
		val savedFilDbData = try {
			repo.saveFilDbData(soknadDto.innsendingsId!!, mapTilFilDb(filDto))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			throw e
		}
		logger.debug("${soknadDto.innsendingsId!!}: Valider størrelse av opplastinger på vedlegg ${filDto.vedleggsid} og søknad ${soknadDto.innsendingsId!!}")
		Validerer().validerStorrelse(
			soknadDto.innsendingsId!!,
			finnFilStorrelseSum(soknadDto, filDto.vedleggsid),
			0,
			restConfig.maxFileSize.toLong(),
			"errorCode.illegalAction.vedleggFileSizeSumTooLarge"
		)
		Validerer().validerStorrelse(
			soknadDto.innsendingsId!!,
			finnFilStorrelseSum(soknadDto),
			0,
			restConfig.maxFileSizeSum.toLong(),
			"errorCode.illegalAction.fileSizeSumTooLarge"
		)
		repo.oppdaterVedleggStatus(
			soknadDto.innsendingsId!!,
			filDto.vedleggsid,
			OpplastingsStatus.LASTET_OPP,
			LocalDateTime.now()
		)
		innsenderMetrics.operationsCounterInc(operation, soknadDto.tema)
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
			throw ResourceNotFoundException(
				null,
				"Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke",
				"errorCode.resourceNotFound.attachmentNotFound"
			)

		val filDbDataOpt = repo.hentFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)

		if (!filDbDataOpt.isPresent)
			when (soknadDto.status.name) {
				SoknadsStatusDto.innsendt.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknad ${soknadDto.innsendingsId} er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til Ditt Nav og søk opp dine saker der"
				)

				SoknadsStatusDto.slettetAvBruker.name, SoknadsStatusDto.automatiskSlettet.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknaden er slettet og ingen filer er tilgjengelig"
				)

				else -> {
					throw ResourceNotFoundException(
						null,
						"Det finnes ikke fil med id=$filId for søknad ${soknadDto.innsendingsId}",
						"errorCode.resourceNotFound.fileNotFound"
					)
				}
			}

		innsenderMetrics.operationsCounterInc(operation, soknadDto.tema)
		return lagFilDto(filDbDataOpt.get())
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
			throw ResourceNotFoundException(
				null,
				"Vedlegg $vedleggsId til søknad $innsendingsId eksisterer ikke",
				"errorCode.resourceNotFound.attachmentNotFound"
			)

		val filDbDataList = if (medFil)
			repo.hentFilerTilVedlegg(innsendingsId, vedleggsId)
		else
			repo.hentFilerTilVedleggUtenFilData(innsendingsId, vedleggsId)

		if (filDbDataList.isEmpty() && kastFeilNarNull)
			when (soknadDto.status.name) {
				SoknadsStatusDto.innsendt.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknad $innsendingsId er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til https://www.nav.no/minside og søk opp dine saker der"
				)

				SoknadsStatusDto.slettetAvBruker.name, SoknadsStatusDto.automatiskSlettet.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknaden er slettet og ingen filer er tilgjengelig"
				)

				else -> {
					throw ResourceNotFoundException(
						null,
						"Ingen filer funnet for oppgitt vedlegg $vedleggsId til søknad $innsendingsId",
						"errorCode.resourceNotFound.fileNotFound"
					)
				}
			}

		return filDbDataList.map { lagFilDto(it, medFil) }
	}

	fun finnFilStorrelseSum(soknadDto: DokumentSoknadDto, vedleggsId: Long): Long {
		return repo.hentSumFilstorrelseTilVedlegg(soknadDto.innsendingsId!!, vedleggsId)
	}

	fun finnFilStorrelseSum(soknadDto: DokumentSoknadDto, vedleggListe: List<VedleggDto> = soknadDto.vedleggsListe) =
		vedleggListe
			.filter { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt || it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
			.sumOf { repo.hentSumFilstorrelseTilVedlegg(soknadDto.innsendingsId!!, it.id!!) }

	@Transactional
	fun slettFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long): VedleggDto {
		val operation = InnsenderOperation.SLETT_FIL.name

		// Sjekk om vedlegget eksisterer
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException(
				null, "Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke",
				"errorCode.resourceNotFound.attachmentNotFound"
			)
		if (repo.hentFilDb(soknadDto.innsendingsId!!, vedleggsId, filId).isEmpty)
			throw ResourceNotFoundException(
				null, "Fil $filId på vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke",
				"errorCode.resourceNotFound.fileNotFound"
			)

		repo.slettFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)
		if (repo.hentFilerTilVedlegg(soknadDto.innsendingsId!!, vedleggsId).isEmpty()) {
			val vedleggDto = soknadDto.vedleggsListe.first { it.id == vedleggsId }
			val nyOpplastingsStatus =
				if (vedleggDto.innsendtdato != null) OpplastingsStatus.INNSENDT else OpplastingsStatus.IKKE_VALGT
			repo.lagreVedlegg(
				mapTilVedleggDb(
					vedleggDto,
					soknadsId = soknadDto.id!!,
					vedleggDto.skjemaurl,
					opplastingsStatus = nyOpplastingsStatus
				)
			)
		}
		val vedleggDto = vedleggService.hentVedleggDto(vedleggsId)
		innsenderMetrics.operationsCounterInc(operation, soknadDto.tema)
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
		if (patchVedleggDto.opplastingsStatus != null && patchVedleggDto.opplastingsStatus != OpplastingsStatusDto.lastetOpp
			&& repo.findAllByVedleggsid(soknadDto.innsendingsId!!, vedleggsId).isNotEmpty()
		) {
			repo.slettFilerForVedlegg(vedleggsId)
			logger.info("Slettet filer til vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} da status er endret til ${patchVedleggDto.opplastingsStatus}")
		}
	}

	fun validerAtMinstEnFilErLastetOpp(soknadDto: DokumentSoknadDto) {
		if (!erEttersending(soknadDto)) {
			// For å sende inn en søknad må det være lastet opp en fil på hoveddokumentet
			val harFil = soknadDto.vedleggsListe
				.filter { it.erHoveddokument && !it.erVariant && it.id != null }
				.map { it.id }
				.any { vedleggService.vedleggHarFiler(soknadDto.innsendingsId!!, it!!) }

			if (!harFil) {
				throw IllegalActionException(
					"Søker må ha lastet opp dokumenter til søknaden for at den skal kunne sendes inn",
					"Innsending avbrutt da hoveddokument ikke finnes",
					"errorCode.illegalAction.sendInErrorNoApplication"
				)
			}

		} else {
			// For å sende inn en ettersendingssøknad må det være lastet opp minst ett vedlegg
			val harFil = soknadDto.vedleggsListe
				.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
				.map { it.id }
				.any { vedleggService.vedleggHarFiler(soknadDto.innsendingsId!!, it!!) }

			if (!harFil) {
				// Hvis status for alle vedlegg som foventes sendt inn er lastetOpp, Innsendt eller SendesAvAndre, ikke kast feil. Merk at kun dummy forside vil bli sendt til arkivet.
				val allePakrevdeBehandlet = soknadDto.vedleggsListe
					.filter { !it.erHoveddokument && ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6") }
					.none { !(it.opplastingsStatus == OpplastingsStatusDto.innsendt || it.opplastingsStatus == OpplastingsStatusDto.sendesAvAndre || it.opplastingsStatus == OpplastingsStatusDto.lastetOpp) }
				if (allePakrevdeBehandlet) {
					val separator = "\n"
					logger.warn("Søker har ikke lastet opp filer på ettersendingssøknad ${soknadDto.innsendingsId}, " +
						"men det er ikke gjenstående arbeid på noen av de påkrevde vedleggene. Vedleggsstatus:\n" +
						soknadDto.vedleggsListe.joinToString(separator) { it.tittel + ", med status = " + it.opplastingsStatus + "\n" })
				} else {
					throw IllegalActionException(
						"Søker må ha ved ettersending til en søknad, ha lastet opp ett eller flere vedlegg for å kunnne sende inn søknaden",
						"Innsending avbrutt da ingen vedlegg er lastet opp",
						"errorCode.illegalAction.sendInErrorNoChange"
					)
				}
			}
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
			medFil = true,
			kastFeilNarNull = false
		).filter { it.data != null }
		if (filer.isEmpty()) return null

		/*
				val vedleggsFil: ByteArray? =
					if (vedleggDto.erHoveddokument && vedleggDto.erVariant) {
						if (filer.size > 1) {
							logger.warn(
								"${soknadDto.innsendingsId}: soknadDtoVedlegg ${vedleggDto.id} er hoveddokument og er variant - " +
									"${vedleggDto.tittel} har flere opplastede filer, velger første"
							)
						}
						filer[0].data
					} else {
						PdfMerger().mergePdfer(filer.map { it.data!! })
					}
		*/

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
		vedleggDbList.forEach {
			val filer = repo.hentFilerTilVedlegg(innsendingsId, it.id!!).filterNot { it.data == null }
			val vedleggsFil =
				if (filer.isEmpty()) {
					logger.info("$innsendingsId: fant ikke opplastede filer til vedlegg ${it.uuid}")
					null
				} else if (filer[0].mimetype == Mimetype.applicationSlashPdf.value) {
					logger.info("$innsendingsId: skal merge ${filer.size} PDFer til vedlegg ${it.uuid}")
					PdfMerger().mergePdfer(filer.map { it.data!! }.toList())
				} else {
					logger.info("$innsendingsId: ikke PDF på vedlegg ${it.uuid}")
					filer[0].data
				}
			logger.info("$innsendingsId: størrelse ${vedleggsFil?.size} til vedlegg ${it.uuid}")
			vedleggDtos.add(lagVedleggDto(it, vedleggsFil))
		}
		return vedleggDtos
	}
}
