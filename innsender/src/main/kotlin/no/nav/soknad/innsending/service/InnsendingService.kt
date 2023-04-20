package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerAPI
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerAPI
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
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
	private val soknadsmottakerAPI: MottakerAPI,
	private val restConfig: RestConfig,
	private val fillagerAPI: FillagerAPI,
	private val exceptionHelper: ExceptionHelper,
	private val ettersendingService: EttersendingService,
	private val brukernotifikasjonService: BrukernotifikasjonService,
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

		filService.validerAtMinstEnFilErLastetOpp(soknadDto)

		// Vedleggsliste med opplastede dokument og status= LASTET_OPP for de som skal sendes soknadsfillager
		val alleVedlegg: List<VedleggDto> = vedleggService.ferdigstillVedlegg(soknadDto)
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
		val kvitteringForArkivering = lagInnsendingsKvittering(soknadDto, opplastedeVedlegg, manglendePakrevdeVedlegg)
		try {
			fillagerAPI.lagreFiler(soknadDto.innsendingsId!!, opplastedeVedlegg + kvitteringForArkivering)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			logger.error("Feil ved sending av filer for søknad ${soknadDto.innsendingsId} ti       l NAV, ${e.message}")
			throw BackendErrorException(
				e.message,
				"Feil ved sending av filer for søknad ${soknadDto.innsendingsId} til NAV",
				"errorCode.backendError.sendToNAVError"
			)
		}

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
			soknadsmottakerAPI.sendInnSoknad(soknadDto, opplastedeVedlegg + kvitteringForArkivering)
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
					it,
					soknadsId = soknadDto.id!!,
					it.skjemaurl,
					opplastingsStatus = OpplastingsStatus.INNSENDT
				)
			)
		}
		manglendePakrevdeVedlegg.forEach {
			repo.oppdaterVedleggStatus(
				soknadDto.innsendingsId!!,
				it.id!!,
				OpplastingsStatus.SEND_SENERE,
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

		try {
			val (opplastet, manglende) = sendInnSoknadStart(soknadDtoInput)

			val innsendtSoknadDto = brukernotifikasjonService.kansellerBrukernotifikasjon(soknadDtoInput)

			ettersendingService.sjekkOgOpprettEttersendingsSoknad(innsendtSoknadDto, manglende, soknadDtoInput)

			return lagKvittering(innsendtSoknadDto, opplastet, manglende)

		} finally {
			innsenderMetrics.operationsCounterInc(operation, soknadDtoInput.tema)
		}
	}

	private fun lagInnsendingsKvittering(
		soknadDto: DokumentSoknadDto,
		opplastedeVedlegg: List<VedleggDto>,
		manglendeVedlegg: List<VedleggDto>
	): VedleggDto {
		val person = pdlInterface.hentPersonData(soknadDto.brukerId)
		val sammensattNavn = listOfNotNull(person?.fornavn, person?.mellomnavn, person?.etternavn).joinToString(" ")

		val kvittering =
			PdfGenerator().lagKvitteringsSide(soknadDto, sammensattNavn.ifBlank { "NN" }, opplastedeVedlegg, manglendeVedlegg)

		return VedleggDto(
			id = null,
			uuid = UUID.randomUUID().toString(),
			vedleggsnr = "L7",
			tittel = "Kvitteringsside for dokumentinnsending",
			label = "Kvitteringsside for dokumentinnsending",
			beskrivelse = null,
			erHoveddokument = false,
			erVariant = true,
			erPdfa = true,
			erPakrevd = false,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			opprettetdato = OffsetDateTime.now(),
			innsendtdato = OffsetDateTime.now(),
			mimetype = Mimetype.applicationSlashPdf,
			document = kvittering,
			skjemaurl = null
		)
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

}
