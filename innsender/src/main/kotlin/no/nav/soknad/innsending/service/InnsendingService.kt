package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.KVITTERINGS_NR
import no.nav.soknad.pdfutilities.PdfGenerator
import no.nav.soknad.pdfutilities.Validerer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Service
class InnsendingService(
	private val repo: RepositoryUtils,
	private val soknadService: SoknadService,
	private val filService: FilService,
	private val vedleggService: VedleggService,
	private val soknadsmottakerAPI: MottakerInterface,
	private val restConfig: RestConfig,
	private val fillagerAPI: FillagerInterface,
	private val exceptionHelper: ExceptionHelper,
	private val ettersendingService: EttersendingService,
	private val brukernotifikasjonPublisher: BrukernotifikasjonPublisher,
	private val innsenderMetrics: InnsenderMetrics,
	private val pdlInterface: PdlInterface,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun sendInnSoknadStart(soknadDtoInput: DokumentSoknadDto): Pair<List<VedleggDto>, List<VedleggDto>> {
		val operation = InnsenderOperation.SEND_INN.name

		// Det er ikke nødvendig å opprette og lagre kvittering(L7) i følge diskusjon 3/11.

		// anta at filene til et vedlegg allerede er konvertert til PDF ved lagring, men må merges og sendes til soknadsfillager
		// dersom det ikke er lastet opp filer på et obligatorisk vedlegg, skal status settes SENDES_SENERE
		// etter at vedleggsfilen er overført soknadsfillager, skal lokalt lagrede filer på vedlegget slettes.
		val soknadDto = if (erEttersending(soknadDtoInput))
			opprettOgLagreDummyHovedDokument(soknadDtoInput)
		else
			soknadDtoInput

		val start = System.currentTimeMillis()
		filService.validerAtMinstEnFilErLastetOpp(soknadDto)
		logger.debug("${soknadDtoInput.innsendingsId}: Tid: validerAtMinstEnFilErLastetOpp = ${System.currentTimeMillis() - start} ")

		// Vedleggsliste med opplastede dokument og status= LASTET_OPP for de som skal sendes soknadsfillager
		val startMergeAvFiler = System.currentTimeMillis()
		val alleVedlegg: List<VedleggDto> = filService.ferdigstillVedleggsFiler(soknadDto)
		logger.debug("${soknadDtoInput.innsendingsId}: Tid: ferdigstillVedleggsFiler = ${System.currentTimeMillis() - startMergeAvFiler} ")

		val opplastedeVedlegg = alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
		val manglendePakrevdeVedlegg =
			alleVedlegg.filter { !it.erHoveddokument && ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6") && (it.opplastingsStatus == OpplastingsStatusDto.sendSenere || it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt) }

		Validerer().validerStorrelse(
			soknadDto.innsendingsId!!,
			filService.finnFilStorrelseSum(soknadDto),
			0,
			restConfig.maxFileSizeSum.toLong(),
			"errorCode.illegalAction.fileSizeSumTooLarge"
		)

		logger.info("${soknadDtoInput.innsendingsId}: Opplastede vedlegg = ${opplastedeVedlegg.map { it.vedleggsnr + ':' + it.uuid + ':' + it.opprettetdato + ':' + it.document?.size }}")
		logger.info("${soknadDtoInput.innsendingsId}: Ikke opplastede påkrevde vedlegg = ${manglendePakrevdeVedlegg.map { it.vedleggsnr + ':' + it.opprettetdato }}")

		val startLagKvittering = System.currentTimeMillis()
		val kvitteringForArkivering =
			lagInnsendingsKvitteringOgLagre(soknadDto, opplastedeVedlegg, manglendePakrevdeVedlegg)
		logger.debug("${soknadDtoInput.innsendingsId}: Tid: lagInnsendingsKvittering = ${System.currentTimeMillis() - startLagKvittering} ")
		logger.debug("${soknadDtoInput.innsendingsId}: skjemanr = ${kvitteringForArkivering.vedleggsnr} (kvittering) med uuid = ${kvitteringForArkivering.uuid}")

		// send soknadmetada til soknadsmottaker
		try {
			val soknadDb = repo.hentSoknadDb(soknadDto.id!!)
			if (soknadDb.get().status == SoknadsStatus.Innsendt) {
				logger.warn("${soknadDto.innsendingsId}: Søknad allerede innsendt, avbryter")
				throw IllegalActionException(
					"Søknaden er allerede sendt inn",
					"Søknaden er innsendt og kan ikke sendes på nytt.",
					"errorCode.illegalAction.applicationSentInOrDeleted"
				)
			}
			soknadsmottakerAPI.sendInnSoknad(soknadDto, (listOf(kvitteringForArkivering) + opplastedeVedlegg))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			logger.error("${soknadDto.innsendingsId}: Feil ved sending av søknad til soknadsmottaker ${e.message}")
			throw BackendErrorException(
				e.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV",
				"errorCode.backendError.sendToNAVError"
			)
		}

		// oppdater vedleggstabellen med status og innsendingsdato for opplastede vedlegg.
		opplastedeVedlegg.forEach {
			repo.lagreVedlegg(
				mapTilVedleggDb(
					vedleggDto = it,
					soknadsId = soknadDto.id!!,
					url = it.skjemaurl,
					opplastingsStatus = OpplastingsStatus.INNSENDT
				)
			)
		}
		manglendePakrevdeVedlegg.forEach {
			repo.oppdaterVedleggStatus(
				innsendingsId = soknadDto.innsendingsId!!,
				vedleggsId = it.id!!,
				opplastingsStatus = OpplastingsStatus.SEND_SENERE,
				LocalDateTime.now()
			)
		}

		try {
			repo.lagreSoknad(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!, SoknadsStatus.Innsendt))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			throw BackendErrorException(
				e.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV",
				"errorCode.backendError.sendToNAVError"
			)
		}
		return Pair(opplastedeVedlegg, manglendePakrevdeVedlegg)
	}

	fun sendInnSoknad(soknadDtoInput: DokumentSoknadDto): KvitteringsDto {
		val operation = InnsenderOperation.SEND_INN.name
		val startSendInn = System.currentTimeMillis()

		try {
			val (opplastet, manglende) = sendInnSoknadStart(soknadDtoInput)

			val innsendtSoknadDto = kansellerBrukernotifikasjon(soknadDtoInput)

			ettersendingService.sjekkOgOpprettEttersendingsSoknad(innsendtSoknadDto, manglende, soknadDtoInput)

			return lagKvittering(innsendtSoknadDto, opplastet, manglende)

		} finally {
			innsenderMetrics.operationsCounterInc(operation, soknadDtoInput.tema)
			logger.debug("${soknadDtoInput.innsendingsId}: Tid: sendInnSoknad = ${System.currentTimeMillis() - startSendInn}")
		}
	}

	private fun lagInnsendingsKvitteringOgLagre(
		soknadDto: DokumentSoknadDto,
		opplastedeVedlegg: List<VedleggDto>,
		manglendeVedlegg: List<VedleggDto>
	): VedleggDto {
		val person = pdlInterface.hentPersonData(soknadDto.brukerId)
		val sammensattNavn = listOfNotNull(person?.fornavn, person?.mellomnavn, person?.etternavn).joinToString(" ")

		val kvittering =
			PdfGenerator().lagKvitteringsSide(soknadDto, sammensattNavn.ifBlank { "NN" }, opplastedeVedlegg, manglendeVedlegg)

		val kvitteringsVedleggDto = VedleggDto(
			id = null,
			uuid = UUID.randomUUID().toString(),
			vedleggsnr = KVITTERINGS_NR,
			tittel = "Kvitteringsside for dokumentinnsending",
			label = "Kvitteringsside for dokumentinnsending",
			beskrivelse = null,
			erHoveddokument = false,
			erVariant = false,
			erPdfa = true,
			erPakrevd = false,
			opplastingsStatus = OpplastingsStatusDto.innsendt,
			opprettetdato = OffsetDateTime.now(),
			innsendtdato = OffsetDateTime.now(),
			mimetype = Mimetype.applicationSlashPdf,
			document = null,
			skjemaurl = null
		)

		val kvitteringsVedlegg = repo.lagreVedlegg(
			mapTilVedleggDb(kvitteringsVedleggDto, soknadDto.id!!)
		)
		repo.saveFilDbData(
			soknadDto.innsendingsId!!,
			FilDbData(
				id = null, vedleggsid = kvitteringsVedlegg.id!!,
				filnavn = "kvittering.pdf", mimetype = Mimetype.applicationSlashPdf.value,
				storrelse = kvittering.size,
				data = kvittering, opprettetdato = kvitteringsVedlegg.opprettetdato
			)
		)

		return kvitteringsVedleggDto
	}

	private fun opprettOgLagreDummyHovedDokument(soknadDto: DokumentSoknadDto): DokumentSoknadDto {
		val operation = InnsenderOperation.SEND_INN.name

		// Hvis ettersending, så må det genereres et dummy hoveddokument
		val dummySkjema = try {
			PdfGenerator().lagForsideEttersending(soknadDto)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			throw BackendErrorException(
				e.message,
				"Feil ved generering av forside for ettersendingssøknad ${soknadDto.innsendingsId}",
				"errorCode.backendError.sendToNAVError"
			)
		}
		val hovedDokumentDto = soknadDto.vedleggsListe.firstOrNull { it.erHoveddokument && !it.erVariant }
			?: lagVedleggDto(
				vedleggService.opprettHovedddokumentVedlegg(
					mapTilSoknadDb(
						soknadDto, soknadDto.innsendingsId!!,
						mapTilSoknadsStatus(soknadDto.status, null)
					),
					KodeverkSkjema(
						tittel = soknadDto.tittel,
						skjemanummer = soknadDto.skjemanr,
						beskrivelse = soknadDto.tittel,
						tema = soknadDto.tema
					)
				), null
			)

		val hovedDokFil = repo.hentFilerTilVedlegg(soknadDto.innsendingsId!!, hovedDokumentDto.id!!)
		if (hovedDokFil.isNotEmpty() && hovedDokFil.first().data != null) {
			return soknadService.hentSoknad(soknadDto.id!!)
		}
		val oppdatertSoknad = soknadService.hentSoknad(soknadDto.id!!)
		filService.lagreFil(
			oppdatertSoknad, FilDto(
				hovedDokumentDto.id!!, null, hovedDokumentDto.vedleggsnr!!, Mimetype.applicationSlashPdf,
				dummySkjema.size, dummySkjema, OffsetDateTime.now()
			)
		)

		return soknadService.hentSoknad(soknadDto.innsendingsId!!)
	}

	private fun lagKvittering(
		innsendtSoknadDto: DokumentSoknadDto,
		opplastedeVedlegg: List<VedleggDto>, manglendePakrevdeVedlegg: List<VedleggDto>
	): KvitteringsDto {
		val hoveddokumentVedleggsId =
			innsendtSoknadDto.vedleggsListe.firstOrNull { it.erHoveddokument && !it.erVariant }?.id
		val hoveddokumentFilId = if (hoveddokumentVedleggsId != null && !erEttersending(innsendtSoknadDto)) {
			repo.findAllByVedleggsid(innsendtSoknadDto.innsendingsId!!, hoveddokumentVedleggsId).firstOrNull()?.id
		} else {
			null
		}
		return KvitteringsDto(innsendtSoknadDto.innsendingsId!!, innsendtSoknadDto.tittel, innsendtSoknadDto.innsendtDato!!,
			lenkeTilDokument(innsendtSoknadDto.innsendingsId!!, hoveddokumentVedleggsId, hoveddokumentFilId),
			opplastedeVedlegg.filter { !it.erHoveddokument }.map { InnsendtVedleggDto(it.vedleggsnr ?: "", it.label) },
			manglendePakrevdeVedlegg.map { InnsendtVedleggDto(it.vedleggsnr ?: "", it.label) },
			innsendtSoknadDto.vedleggsListe
				.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.sendesAvAndre }
				.map { InnsendtVedleggDto(it.vedleggsnr ?: "", it.label) },
			beregnInnsendingsfrist(innsendtSoknadDto)
		)
	}

	private fun beregnInnsendingsfrist(innsendtSoknadDto: DokumentSoknadDto): OffsetDateTime {
		if (erEttersending(innsendtSoknadDto)) {
			return innsendtSoknadDto.forsteInnsendingsDato!!.plusDays(
				innsendtSoknadDto.fristForEttersendelse ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
			)
		} else {
			return innsendtSoknadDto.innsendtDato!!.plusDays(
				innsendtSoknadDto.fristForEttersendelse ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
			)
		}
	}

	private fun lenkeTilDokument(innsendingsId: String, vedleggsId: Long?, filId: Long?) =
		if (filId == null) null else "frontend/v1/soknad/$innsendingsId/vedlegg/$vedleggsId/fil/$filId"

	private fun kansellerBrukernotifikasjon(soknadDtoInput: DokumentSoknadDto): DokumentSoknadDto {
		// send brukernotifikasjon ved endring av søknadsstatus til innsendt
		val innsendtSoknadDto = soknadService.hentSoknad(soknadDtoInput.innsendingsId!!)
		logger.info("${innsendtSoknadDto.innsendingsId}: Sendinn: innsendtdato på vedlegg med status innsendt= " +
			innsendtSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt }
				.map { it.vedleggsnr + ":" + mapTilLocalDateTime(it.innsendtdato) }
		)
		publiserBrukernotifikasjon(innsendtSoknadDto)
		return innsendtSoknadDto
	}

	private fun publiserBrukernotifikasjon(dokumentSoknadDto: DokumentSoknadDto): Boolean = try {
		brukernotifikasjonPublisher.soknadStatusChange(dokumentSoknadDto)
	} catch (e: Exception) {
		throw BackendErrorException(
			e.message,
			"Feil i ved avslutning av brukernotifikasjon for søknad ${dokumentSoknadDto.tittel}",
			"errorCode.backendError.sendToNAVError"
		)
	}


	fun getFiles(innsendingId: String, vedleggUrls: List<String>): List<SoknadFile> {
		val timer = innsenderMetrics.operationHistogramLatencyStart(InnsenderOperation.HENT.name)

		logger.info("$innsendingId: Skal hente ${vedleggUrls.joinToString(",")}")
		try {
			val hendelseDbData = repo.hentHendelse(innsendingId, null)

			if (hendelseDbData.isEmpty()) {
				logger.info("$innsendingId: ikke funnet innslag for søknad i hendelsesloggen")
				return vedleggUrls.map { SoknadFile(id = it, content = null, createdAt = null, status = statusNotFound) }
					.toList()
			}
			val erArkivert = hendelseDbData.any { it.hendelsetype == HendelseType.Arkivert }
			val soknadDbData = repo.hentSoknadDb(innsendingId)
			if (!soknadDbData.isPresent || erArkivert) {
				logger.info("$innsendingId: søknaden er slettet eller allerede arkivert")
				return vedleggUrls.map { SoknadFile(id = it, content = null, createdAt = null, status = statusDeleted) }
					.toList()
			}

			val vedleggDbDatas = repo.hentAlleVedleggGittSoknadsid(soknadDbData.get().id!!)
			val soknadVedleggUrls = vedleggDbDatas.map { it.uuid }.toList()
			if (vedleggUrls.any { !soknadVedleggUrls.contains(it) }) {
				logger.warn("$innsendingId: Forsøk på henting av vedlegg som ikke eksisterer for angitt søknad")
				return vedleggUrls.map { SoknadFile(id = it, content = null, createdAt = null, status = statusNotFound) }
					.toList()
			}

			val mergedVedlegg = mergeOgReturnerVedlegg(innsendingId, vedleggUrls, vedleggDbDatas)

			if (mergedVedlegg.any { it.document == null }) {
				logger.warn(
					"$innsendingId: Følgende vedlegg mangler opplastet fil: ${
						mergedVedlegg.filter { it.document == null }.map { it.uuid }
					}"
				)
			}

			// Har vedleggurls og matchende vedleggsliste med filene som skal returneres til soknadsarkiverer
			val idResult = mapToSoknadFiles(vedleggUrls, mergedVedlegg, erArkivert, innsendingId)

			val hentet = idResult.map { it.id + "-" + it.status }.joinToString(",")
			logger.info("$innsendingId: Hentet $hentet")
			return idResult

		} finally {
			innsenderMetrics.operationHistogramLatencyEnd(timer)
		}
	}

	private fun mapToSoknadFiles(
		vedleggUrls: List<String>,
		mergedVedlegg: List<VedleggDto>,
		erArkivert: Boolean,
		innsendingId: String
	): List<SoknadFile> {
		val idResult = vedleggUrls
			.map { vedleggurl ->
				mergedVedlegg.firstOrNull { it.uuid == vedleggurl } ?: VedleggDto(
					tittel = "",
					label = "",
					erHoveddokument = false,
					uuid = vedleggurl,
					erVariant = false,
					erPdfa = false,
					erPakrevd = false,
					opplastingsStatus = OpplastingsStatusDto.ikkeValgt,
					opprettetdato = OffsetDateTime.now(),
					document = null
				)
			}
			.map {
				SoknadFile(
					id = it.uuid!!,
					status = if (it.document != null) statusOk else if (erArkivert) statusDeleted else statusNotFound,
					content = it.document,
					createdAt = it.innsendtdato
				)
			}
			.onEach {
				if (it.status != statusOk) {
					logger.info("$innsendingId: Failed to find vedlegg with id '${it.id}' in database")
				}
			}
		return idResult
	}

	private val statusOk = "ok"
	private val statusDeleted = "deleted"
	private val statusNotFound = "not-found"

	private fun mergeOgReturnerVedlegg(
		innsendingId: String,
		vedleggsUrls: List<String>,
		soknadVedleggs: List<VedleggDbData>
	): List<VedleggDto> {
		return filService.hentOgMergeVedleggsFiler(
			innsendingId,
			soknadVedleggs.filter { vedleggsUrls.contains(it.uuid) }.toList()
		)

	}

}
