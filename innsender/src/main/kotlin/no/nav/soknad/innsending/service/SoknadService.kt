package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.*
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.pdfutilities.PdfGenerator
import no.nav.soknad.pdfutilities.PdfMerger
import no.nav.soknad.pdfutilities.Validerer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Service
class SoknadService(
	private val repo: RepositoryUtils,
	private val skjemaService: SkjemaService,
	private val vedleggService: VedleggService,
	private val ettersendingService: EttersendingService,
	private val filService: FilService,
	private val brukerNotifikasjon: BrukernotifikasjonPublisher,
	private val fillagerAPI: FillagerInterface,
	private val soknadsmottakerAPI: MottakerInterface,
	private val innsenderMetrics: InnsenderMetrics,
	private val pdlInterface: PdlInterface,
	private val restConfig: RestConfig,
	private val exceptionHelper: ExceptionHelper,
	) {

	@Value("\${ettersendingsfrist}")
	private var ettersendingsfrist: Long = 14

	private val logger = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun opprettSoknad(brukerId: String, skjemanr: String, spraak: String, vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		// hentSkjema informasjon gitt skjemanr
		val kodeverkSkjema = skjemaService.hentSkjema(skjemanr, spraak)

		try {
			// lagre soknad
			val savedSoknadDbData = repo.lagreSoknad(
				SoknadDbData(
					null, Utilities.laginnsendingsId(), kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: "",
					kodeverkSkjema.tema ?: "", spraak, SoknadsStatus.Opprettet, brukerId, null, LocalDateTime.now(),
					LocalDateTime.now(), null, 0, VisningsType.dokumentinnsending, true,
					forsteinnsendingsdato = null, ettersendingsfrist = Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE,
					arkiveringsstatus = ArkiveringsStatus.IkkeSatt
				)
			)

			// Lagre soknadens hovedvedlegg
			val skjemaDbData = vedleggService.opprettHovedddokumentVedlegg(savedSoknadDbData, kodeverkSkjema)

			val vedleggDbDataListe = vedleggService.opprettVedleggTilSoknad(savedSoknadDbData.id!!, vedleggsnrListe, spraak)

			val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe

			val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			return dokumentSoknadDto
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, kodeverkSkjema.tema ?: "Ukjent")
			throw e
		} finally {
			innsenderMetrics.operationsCounterInc(operation, kodeverkSkjema.tema ?: "Ukjent")
		}
	}

	@Transactional
	fun opprettSoknadForEttersendingGittSkjemanr(brukerId: String, skjemanr: String, spraak: String = "nb", vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name
		logger.info("opprettSoknadForEttersendingGittSkjemanr: for skjemanr=$skjemanr")

		val kodeverkSkjema = try {
			// hentSkjema informasjon gitt skjemanr
			skjemaService.hentSkjema(skjemanr, finnSpraakFraInput(spraak))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, "Ukjent")
			throw e
		}

		try {
			// lagre soknad
			val ettersendingsSoknadDb = ettersendingService.opprettEttersendingsSoknad(brukerId = brukerId, ettersendingsId = null,
				kodeverkSkjema.tittel ?: "", skjemanr, kodeverkSkjema.tema ?: "", spraak, OffsetDateTime.now())

			// For hvert vedleggsnr hent definisjonen fra Sanity og lagr vedlegg.
			val vedleggDbDataListe = vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, vedleggsnrListe, spraak, null)

			val dokumentSoknadDto = lagDokumentSoknadDto(ettersendingsSoknadDb, vedleggDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return dokumentSoknadDto
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, kodeverkSkjema.tema ?: "Ukjent")
			throw e
		} finally {
			innsenderMetrics.operationsCounterInc(operation, kodeverkSkjema.tema ?: "Ukjent")
		}
	}

	@Transactional
	fun opprettSoknadForettersendingAvVedlegg(brukerId: String, ettersendingsId: String): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		// Skal opprette en soknad basert på status på vedlegg som skal ettersendes.
		// Basere opplastingsstatus på nyeste innsending på ettersendingsId, dvs. nyeste soknad der innsendingsId eller ettersendingsId lik oppgitt ettersendingsId
		// Det skal være mulig å ettersende allerede ettersendte vedlegg på nytt
		val soknadDbDataList = try {
			repo.finnNyesteSoknadGittEttersendingsId(ettersendingsId)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, "Ukjent")
			throw BackendErrorException(e.message, "Feil ved henting av søknad $ettersendingsId", "errorCode.backendError.applicationFetchError")
		}

		if (soknadDbDataList.isEmpty()) {
			exceptionHelper.reportException(Exception("No SoknadDbData found"), operation, "Ukjent")
			throw ResourceNotFoundException(
				"Kan ikke opprette søknad for ettersending",
				"Soknad med id $ettersendingsId som det skal ettersendes data for ble ikke funnet",
				"errorCode.resourceNotFound.applicationUnknown"
			)
		}

		sjekkHarAlleredeSoknadUnderArbeid(brukerId, soknadDbDataList.first().skjemanr, true)
		return ettersendingService.opprettEttersendingsSoknad(vedleggService.hentAlleVedlegg(soknadDbDataList.first()), ettersendingsId)
	}

	@Transactional
	fun opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg(
		brukerId: String, nyesteSoknad: DokumentSoknadDto, sprak: String, vedleggsnrListe: List<String>): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		try {
			logger.info("opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg fra ${nyesteSoknad.innsendingsId} og vedleggsliste = $vedleggsnrListe")
			val ettersendingsSoknadDb = ettersendingService.opprettEttersendingsSoknad(brukerId, nyesteSoknad.ettersendingsId ?: nyesteSoknad.innsendingsId!!,
				nyesteSoknad.tittel, nyesteSoknad.skjemanr, nyesteSoknad.tema, nyesteSoknad.spraak!!,
				nyesteSoknad.forsteInnsendingsDato ?: nyesteSoknad.innsendtDato ?: nyesteSoknad.endretDato ?: nyesteSoknad.opprettetDato,
				nyesteSoknad.fristForEttersendelse)

			val nyesteSoknadVedleggsNrListe = nyesteSoknad.vedleggsListe.filter { !it.erHoveddokument }.map { it.vedleggsnr }
			val filtrertVedleggsnrListe = vedleggsnrListe.filter { !nyesteSoknadVedleggsNrListe.contains(it) }

			val vedleggDbDataListe = vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, filtrertVedleggsnrListe, sprak)

			val innsendtDbDataListe = vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb, nyesteSoknad.vedleggsListe)

			val dokumentSoknadDto = lagDokumentSoknadDto(ettersendingsSoknadDb, vedleggDbDataListe + innsendtDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return dokumentSoknadDto
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, nyesteSoknad.tema)
			throw e
		} finally {
			innsenderMetrics.operationsCounterInc(operation, nyesteSoknad.tema)
		}
	}

	@Transactional
	fun opprettSoknadForettersendingAvVedleggGittArkivertSoknadOgVedlegg(
		brukerId: String, arkivertSoknad: AktivSakDto, opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr,
		sprak: String?, forsteInnsendingsDato: OffsetDateTime?): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		logger.info("opprettSoknadForettersendingAvVedleggGittArkivertSoknadOgVedlegg: for skjemanr=${arkivertSoknad.skjemanr}")
		try {
			val ettersendingsSoknadDb = ettersendingService.opprettEttersendingsSoknad(brukerId = brukerId, ettersendingsId = arkivertSoknad.innsendingsId,
				tittel = arkivertSoknad.tittel, skjemanr = arkivertSoknad.skjemanr, tema = arkivertSoknad.tema, sprak = sprak ?: "nb",
				forsteInnsendingsDato ?: arkivertSoknad.innsendtDato)

			val nyesteSoknadVedleggsNrListe = arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != arkivertSoknad.skjemanr }.map { it.vedleggsnr }
			val filtrertVedleggsnrListe = opprettEttersendingGittSkjemaNr.vedleggsListe?.filter { !nyesteSoknadVedleggsNrListe.contains(it) }.orEmpty()

			val vedleggDbDataListe = vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, filtrertVedleggsnrListe, sprak ?: "nb" )

			val innsendtDbDataListe = vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb, arkivertSoknad)

			val dokumentSoknadDto = lagDokumentSoknadDto(ettersendingsSoknadDb, vedleggDbDataListe + innsendtDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return dokumentSoknadDto
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, arkivertSoknad.tema)
			throw e
		} finally {
			innsenderMetrics.operationsCounterInc(operation, arkivertSoknad.tema)
		}
	}

	@Transactional
	fun opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(brukerId: String, arkivertSoknad: AktivSakDto, sprak: String, vedleggsnrListe: List<String>): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		try {
			val innsendingsId = Utilities.laginnsendingsId()
			// lagre soknad
			val savedSoknadDbData = repo.lagreSoknad(
				SoknadDbData(
					null,
					innsendingsId,
					arkivertSoknad.tittel,
					arkivertSoknad.skjemanr,
					arkivertSoknad.tema,
					finnSpraakFraInput(sprak),
					SoknadsStatus.Opprettet,
					brukerId,
					arkivertSoknad.innsendingsId ?: innsendingsId, // har ikke referanse til tidligere innsendt søknad, bruker søknadens egen innsendingsId istedenfor
					LocalDateTime.now(),
					LocalDateTime.now(),
					null,
					0,
					VisningsType.ettersending,
					true,
					mapTilLocalDateTime(arkivertSoknad.innsendtDato),
					Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE,
					arkiveringsstatus = ArkiveringsStatus.IkkeSatt
				)
			)

			val innsendtVedleggsnrListe: List<String> = arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != arkivertSoknad.skjemanr }.map { it.vedleggsnr }
			// Opprett vedlegg til ettersendingssøknaden gitt spesifiserte skjemanr som ikke er funnet i nyeste relaterte arkiverte søknad.
			val vedleggDbDataListe = vedleggService.opprettVedleggTilSoknad(savedSoknadDbData.id!!, vedleggsnrListe.filter { !innsendtVedleggsnrListe.contains(it) }, sprak)
			// Opprett vedlegg til ettersendingssøknad gitt vedlegg i nyeste arkiverte søknad for spesifisert skjemanummer
			val innsendtVedleggDbDataListe = vedleggService.opprettInnsendteVedleggTilSoknad(savedSoknadDbData.id, arkivertSoknad)
			val savedVedleggDbDataListe = vedleggDbDataListe + innsendtVedleggDbDataListe

			val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)
			publiserBrukernotifikasjon(dokumentSoknadDto)

			return dokumentSoknadDto
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, arkivertSoknad.tema)
			throw e
		} finally {
			innsenderMetrics.operationsCounterInc(operation, arkivertSoknad.tema)
		}
	}

	@Transactional
	fun opprettNySoknad(dokumentSoknadDto: DokumentSoknadDto): String {
		val operation = InnsenderOperation.OPPRETT.name

		val innsendingsId = Utilities.laginnsendingsId()
		try {
			val savedSoknadDbData = repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId))
			val soknadsid = savedSoknadDbData.id
			val savedVedleggDbData = dokumentSoknadDto.vedleggsListe
				.map { repo.lagreVedlegg(mapTilVedleggDb(it, soknadsid!!)) }

			val savedDokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbData)
			// lagre mottatte filer i fil tabellen.
			savedDokumentSoknadDto.vedleggsListe
				.filter { it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
				.forEach { filService.lagreFil(savedDokumentSoknadDto, it, dokumentSoknadDto.vedleggsListe) }
			publiserBrukernotifikasjon(savedDokumentSoknadDto)

			innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
			return innsendingsId
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, dokumentSoknadDto.tema)
			throw e
		}
	}

	fun hentAktiveSoknader(brukerIds: List<String>): List<DokumentSoknadDto>  {
		return brukerIds.flatMap { hentSoknadGittBrukerId(it) }
	}

	fun hentInnsendteSoknader(brukerIds: List<String>): List<DokumentSoknadDto>  {
		return brukerIds.flatMap { hentSoknadGittBrukerId(it, SoknadsStatus.Innsendt) }
	}

	private fun hentSoknadGittBrukerId(brukerId: String, soknadsStatus: SoknadsStatus = SoknadsStatus.Opprettet): List<DokumentSoknadDto> {
		val soknader = repo.finnAlleSoknaderGittBrukerIdOgStatus(brukerId, soknadsStatus)

		return soknader.map { vedleggService.hentAlleVedlegg(it) }
	}

	// Hent soknad gitt id med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(id: Long): DokumentSoknadDto {
		val soknadDbDataOpt = repo.hentSoknadDb(id)
		return vedleggService.hentAlleVedlegg(soknadDbDataOpt, id.toString())
	}

	// Hent soknad gitt innsendingsid med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(innsendingsId: String): DokumentSoknadDto {
		val soknadDbDataOpt = repo.hentSoknadDb(innsendingsId)
		return vedleggService.hentAlleVedlegg(soknadDbDataOpt, innsendingsId)
	}

	fun endreSoknad(id: Long, visningsSteg: Long) {
		repo.endreSoknadDb(id, visningsSteg)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAvBruker(dokumentSoknadDto: DokumentSoknadDto) {
		val operation = InnsenderOperation.SLETT.name

		// slett vedlegg og soknad
		if (dokumentSoknadDto.status != SoknadsStatusDto.opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den er innsendt eller slettet")

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { vedleggService.slettVedleggOgDensFiler(it) }
		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		repo.slettSoknad(dokumentSoknadDto)

		val soknadDbData = mapTilSoknadDb(dokumentSoknadDto, dokumentSoknadDto.innsendingsId!!, SoknadsStatus.SlettetAvBruker)
		val slettetSoknad = lagDokumentSoknadDto(soknadDbData, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!) })
		publiserBrukernotifikasjon(slettetSoknad)
		innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAutomatisk(innsendingsId: String) {
		val operation = InnsenderOperation.SLETT.name

		// Ved automatisk sletting beholdes innslag i basen, men eventuelt opplastede filer slettes
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (dokumentSoknadDto.status != SoknadsStatusDto.opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den allerede er innsendt")

		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettFilerForVedlegg(it.id!!) }
		val slettetSoknadDb = repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.AutomatiskSlettet))

		val slettetSoknadDto = lagDokumentSoknadDto(slettetSoknadDb,
			dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!) })
		publiserBrukernotifikasjon(slettetSoknadDto)
		logger.info("slettSoknadAutomatisk: Status for søknad $innsendingsId er satt til ${SoknadsStatus.AutomatiskSlettet}")

		innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
	}

	@Transactional
	fun slettSoknadPermanent(innsendingsId: String) {
		val operation = InnsenderOperation.SLETT.name

		val dokumentSoknadDto = hentSoknad(innsendingsId)

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettFilerForVedlegg(it.id!!) }
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettVedlegg(it.id!!) }
		repo.slettSoknad(dokumentSoknadDto)

		logger.info("slettSoknadPermanent: Søknad $innsendingsId er permanent slettet")

		innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
	}

	fun slettGamleSoknader(dagerGamle: Long, permanent: Boolean = false ) {
		val slettFor = mapTilOffsetDateTime(LocalDateTime.now(), -dagerGamle)
		logger.info("Finn opprettede søknader opprettet før $slettFor permanent=$permanent")
		if (permanent) {
			val soknadDbDataListe = repo.findAllByOpprettetdatoBefore(slettFor)
			logger.info("SlettPermanentSoknader: Funnet ${soknadDbDataListe.size} søknader som skal permanent slettes")
			soknadDbDataListe.forEach { slettSoknadPermanent(it.innsendingsid) }
		} else {
			val soknadDbDataListe = repo.findAllByStatusAndWithOpprettetdatoBefore(SoknadsStatus.Opprettet.name, slettFor)
			logger.info("SlettGamleIkkeInnsendteSoknader: Funnet ${soknadDbDataListe.size} søknader som skal slettes")
			soknadDbDataListe.forEach { slettSoknadAutomatisk(it.innsendingsid) }
		}
	}

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
		val manglendePakrevdeVedlegg = alleVedlegg.filter { !it.erHoveddokument && ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6") && (it.opplastingsStatus == OpplastingsStatusDto.sendSenere || it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt) }

		Validerer().validerStorrelse(soknadDto.innsendingsId!!, filService.finnFilStorrelseSum(soknadDto), 0, restConfig.maxFileSizeSum.toLong(), "errorCode.illegalAction.fileSizeSumTooLarge" )

		logger.info("${soknadDtoInput.innsendingsId}: Opplastede vedlegg = ${opplastedeVedlegg.map { it.vedleggsnr+':'+it.uuid+':'+it.opprettetdato+':'+it.document?.size }}")
		logger.info("${soknadDtoInput.innsendingsId}: Ikke opplastede påkrevde vedlegg = ${manglendePakrevdeVedlegg.map { it.vedleggsnr+':'+it.opprettetdato }}")
		val kvitteringForArkivering = lagInnsendingsKvittering(soknadDto, opplastedeVedlegg, manglendePakrevdeVedlegg)
		try {
			fillagerAPI.lagreFiler(soknadDto.innsendingsId!!, opplastedeVedlegg + kvitteringForArkivering)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			logger.error("Feil ved sending av filer for søknad ${soknadDto.innsendingsId} ti       l NAV, ${e.message}")
			throw BackendErrorException(e.message, "Feil ved sending av filer for søknad ${soknadDto.innsendingsId} til NAV", "errorCode.backendError.sendToNAVError")
		}

		// send soknadmetada til soknadsmottaker
		try {
			val soknadDb = repo.hentSoknadDb(soknadDto.id!!)
			if (soknadDb.get().status == SoknadsStatus.Innsendt) {
				logger.warn("${soknadDto.innsendingsId}: Søknad allerede innsendt, avbryter")
				throw IllegalActionException(
					"Søknaden er allerede sendt inn",
					"Søknaden er innsendt og kan ikke sendes på nytt.",
					"errorCode.illegalAction.applicationSentInOrDeleted")
			}
			soknadsmottakerAPI.sendInnSoknad(soknadDto, opplastedeVedlegg + kvitteringForArkivering)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			logger.error("${soknadDto.innsendingsId}: Feil ved sending av søknad til soknadsmottaker ${e.message}")
			throw BackendErrorException(e.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV",
				"errorCode.backendError.sendToNAVError")
		}

		// oppdater vedleggstabellen med status og innsendingsdato for opplastede vedlegg.
		opplastedeVedlegg.forEach { repo.lagreVedlegg(mapTilVedleggDb(it, soknadsId = soknadDto.id!!, it.skjemaurl, opplastingsStatus = OpplastingsStatus.INNSENDT)) }
		manglendePakrevdeVedlegg.forEach { repo.oppdaterVedleggStatus(soknadDto.innsendingsId!!, it.id!!, OpplastingsStatus.SEND_SENERE, LocalDateTime.now()) }

		try {
			repo.lagreSoknad(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!, SoknadsStatus.Innsendt))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			throw BackendErrorException(e.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV",
				"errorCode.backendError.sendToNAVError")
		}
		return Pair(opplastedeVedlegg, manglendePakrevdeVedlegg)
	}

	fun sendInnSoknad(soknadDtoInput: DokumentSoknadDto): KvitteringsDto {
		val operation = InnsenderOperation.SEND_INN.name

		try {
			val (opplastet, manglende) = sendInnSoknadStart(soknadDtoInput)

			val innsendtSoknadDto = kansellerBrukernotifikasjon(soknadDtoInput)

			ettersendingService.sjekkOgOpprettEttersendingsSoknad(innsendtSoknadDto, manglende, soknadDtoInput)

			return lagKvittering(innsendtSoknadDto, opplastet, manglende)

		} finally {
			innsenderMetrics.operationsCounterInc(operation, soknadDtoInput.tema)
		}
	}

	private fun lagInnsendingsKvittering(soknadDto: DokumentSoknadDto, opplastedeVedlegg: List<VedleggDto>, manglendeVedlegg: List<VedleggDto>): VedleggDto {
		val person = pdlInterface.hentPersonData(soknadDto.brukerId)
		val sammensattNavn = listOfNotNull(person?.fornavn, person?.mellomnavn, person?.etternavn).joinToString(" ")

		val kvittering = PdfGenerator().lagKvitteringsSide(soknadDto, sammensattNavn.ifBlank { "NN" }, opplastedeVedlegg, manglendeVedlegg)

		return VedleggDto(id = null, uuid = UUID.randomUUID().toString(), vedleggsnr = "L7",
			tittel = "Kvitteringsside for dokumentinnsending", label = "Kvitteringsside for dokumentinnsending", beskrivelse = null,
			erHoveddokument = false, erVariant = true, erPdfa = true, erPakrevd = false,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp, opprettetdato = OffsetDateTime.now(), innsendtdato = OffsetDateTime.now(),
			mimetype = Mimetype.applicationSlashPdf, document = kvittering, skjemaurl = null
		)
	}

	private fun kansellerBrukernotifikasjon(soknadDtoInput: DokumentSoknadDto): DokumentSoknadDto {
		// send brukernotifikasjon ved endring av søknadsstatus til innsendt
		val innsendtSoknadDto = hentSoknad(soknadDtoInput.innsendingsId!!)
		logger.info("${innsendtSoknadDto.innsendingsId}: Sendinn: innsendtdato på vedlegg med status innsendt= " +
			innsendtSoknadDto.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt }
				.map { it.vedleggsnr + ":" + mapTilLocalDateTime(it.innsendtdato) }
		)
		publiserBrukernotifikasjon(innsendtSoknadDto)
		return innsendtSoknadDto
	}

	private fun opprettOgLagreDummyHovedDokument(soknadDto: DokumentSoknadDto): DokumentSoknadDto {
		val operation = InnsenderOperation.SEND_INN.name

		// Hvis ettersending, så må det genereres et dummy hoveddokument
		val dummySkjema = try {
			PdfGenerator().lagForsideEttersending(soknadDto)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, soknadDto.tema)
			throw BackendErrorException(e.message, "Feil ved generering av forside for ettersendingssøknad ${soknadDto.innsendingsId}", "errorCode.backendError.sendToNAVError")
		}
		val hovedDokumentDto = soknadDto.vedleggsListe.firstOrNull { it.erHoveddokument && !it.erVariant }
			?: lagVedleggDto(vedleggService.opprettHovedddokumentVedlegg(
					mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!,
					mapTilSoknadsStatus(soknadDto.status, null)),
					KodeverkSkjema(tittel = soknadDto.tittel, skjemanummer = soknadDto.skjemanr, beskrivelse = soknadDto.tittel, tema = soknadDto.tema ) ), null)

		val hovedDokFil = repo.hentFilerTilVedlegg(soknadDto.innsendingsId!!, hovedDokumentDto.id!!)
		if (hovedDokFil.isNotEmpty() && hovedDokFil.first().data != null) {
			return hentSoknad(soknadDto.id!!)
		}
		val oppdatertSoknad = hentSoknad(soknadDto.id!!)
		filService.lagreFil(
			oppdatertSoknad, FilDto(
					hovedDokumentDto.id!!, null, hovedDokumentDto.vedleggsnr!!, Mimetype.applicationSlashPdf,
					dummySkjema.size, dummySkjema, OffsetDateTime.now()
				)
			)

		return hentSoknad(soknadDto.innsendingsId!!)
	}

	private fun publiserBrukernotifikasjon(dokumentSoknadDto: DokumentSoknadDto): Boolean = try {
		brukerNotifikasjon.soknadStatusChange(dokumentSoknadDto)
	} catch (e: Exception) {
		throw BackendErrorException(
			e.message,
			"Feil i ved avslutning av brukernotifikasjon for søknad ${dokumentSoknadDto.tittel}",
			"errorCode.backendError.sendToNAVError"
		)
	}

	private fun lagKvittering(innsendtSoknadDto: DokumentSoknadDto,
														opplastedeVedlegg: List<VedleggDto>, manglendePakrevdeVedlegg: List<VedleggDto>): KvitteringsDto {
		val hoveddokumentVedleggsId = innsendtSoknadDto.vedleggsListe.firstOrNull { it.erHoveddokument && !it.erVariant }?.id
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
			return innsendtSoknadDto.forsteInnsendingsDato!!.plusDays(innsendtSoknadDto.fristForEttersendelse ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE)
		} else {
			return innsendtSoknadDto.innsendtDato!!.plusDays(innsendtSoknadDto.fristForEttersendelse ?: Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE)
		}
	}

	private fun lenkeTilDokument(innsendingsId: String, vedleggsId: Long?, filId: Long?) = if (filId == null) null else "frontend/v1/soknad/$innsendingsId/vedlegg/$vedleggsId/fil/$filId"


	fun sjekkHarAlleredeSoknadUnderArbeid(brukerId: String, skjemanr: String, ettersending: Boolean ) {

		val aktiveSoknaderGittSkjemanr = hentAktiveSoknader(listOf( brukerId)).filter { it.skjemanr == skjemanr && erEttersending(it) == ettersending }

		if (aktiveSoknaderGittSkjemanr.isNotEmpty()) {
			logger.warn("Dupliserer søknad på skjemanr=$skjemanr, søker har allerede ${aktiveSoknaderGittSkjemanr.size} under arbeid")
		}

	}

}
