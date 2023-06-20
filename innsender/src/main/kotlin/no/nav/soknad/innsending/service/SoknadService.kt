package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.ArkiveringsStatus
import no.nav.soknad.innsending.repository.HendelseType
import no.nav.soknad.innsending.repository.SoknadDbData
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import no.nav.soknad.innsending.util.validators.validerSoknadVedOppdatering
import no.nav.soknad.innsending.util.validators.validerVedleggsListeVedOppdatering
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime

@Service
class SoknadService(
	private val skjemaService: SkjemaService,
	private val repo: RepositoryUtils,
	private val vedleggService: VedleggService,
	private val ettersendingService: EttersendingService,
	private val filService: FilService,
	private val brukernotifikasjonPublisher: BrukernotifikasjonPublisher,
	private val innsenderMetrics: InnsenderMetrics,
	private val exceptionHelper: ExceptionHelper,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun opprettSoknad(
		brukerId: String,
		skjemanr: String,
		spraak: String,
		vedleggsnrListe: List<String> = emptyList()
	): DokumentSoknadDto {
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
	fun opprettSoknadForEttersendingGittSkjemanr(
		brukerId: String,
		skjemanr: String,
		spraak: String = "nb",
		vedleggsnrListe: List<String> = emptyList()
	): DokumentSoknadDto {
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
			val ettersendingsSoknadDb = ettersendingService.opprettEttersendingsSoknad(
				brukerId = brukerId, ettersendingsId = null,
				kodeverkSkjema.tittel ?: "", skjemanr, kodeverkSkjema.tema ?: "", spraak, OffsetDateTime.now()
			)

			// For hvert vedleggsnr hent definisjonen fra Sanity og lagr vedlegg.
			val vedleggDbDataListe =
				vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, vedleggsnrListe, spraak, null)

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
			throw BackendErrorException(
				e.message,
				"Feil ved henting av søknad $ettersendingsId",
				"errorCode.backendError.applicationFetchError"
			)
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
		return ettersendingService.opprettEttersendingsSoknad(
			vedleggService.hentAlleVedlegg(soknadDbDataList.first()),
			ettersendingsId
		)
	}

	@Transactional
	fun opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg(
		brukerId: String, nyesteSoknad: DokumentSoknadDto, sprak: String, vedleggsnrListe: List<String>
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		try {
			logger.info("opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg fra ${nyesteSoknad.innsendingsId} og vedleggsliste = $vedleggsnrListe")
			val ettersendingsSoknadDb = ettersendingService.opprettEttersendingsSoknad(
				brukerId, nyesteSoknad.ettersendingsId ?: nyesteSoknad.innsendingsId!!,
				nyesteSoknad.tittel, nyesteSoknad.skjemanr, nyesteSoknad.tema, nyesteSoknad.spraak!!,
				nyesteSoknad.forsteInnsendingsDato ?: nyesteSoknad.innsendtDato ?: nyesteSoknad.endretDato
				?: nyesteSoknad.opprettetDato,
				nyesteSoknad.fristForEttersendelse
			)

			val nyesteSoknadVedleggsNrListe = nyesteSoknad.vedleggsListe.filter { !it.erHoveddokument }.map { it.vedleggsnr }
			val filtrertVedleggsnrListe = vedleggsnrListe.filter { !nyesteSoknadVedleggsNrListe.contains(it) }

			val vedleggDbDataListe =
				vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, filtrertVedleggsnrListe, sprak)

			val innsendtDbDataListe =
				vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb, nyesteSoknad.vedleggsListe)

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
		sprak: String?, forsteInnsendingsDato: OffsetDateTime?
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		logger.info("opprettSoknadForettersendingAvVedleggGittArkivertSoknadOgVedlegg: for skjemanr=${arkivertSoknad.skjemanr}")
		try {
			val ettersendingsSoknadDb = ettersendingService.opprettEttersendingsSoknad(
				brukerId = brukerId,
				ettersendingsId = arkivertSoknad.innsendingsId,
				tittel = arkivertSoknad.tittel,
				skjemanr = arkivertSoknad.skjemanr,
				tema = arkivertSoknad.tema,
				sprak = sprak ?: "nb",
				forsteInnsendingsDato ?: arkivertSoknad.innsendtDato
			)

			val nyesteSoknadVedleggsNrListe =
				arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != arkivertSoknad.skjemanr }.map { it.vedleggsnr }
			val filtrertVedleggsnrListe =
				opprettEttersendingGittSkjemaNr.vedleggsListe?.filter { !nyesteSoknadVedleggsNrListe.contains(it) }.orEmpty()

			val vedleggDbDataListe =
				vedleggService.opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, filtrertVedleggsnrListe, sprak ?: "nb")

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
	fun opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(
		brukerId: String,
		arkivertSoknad: AktivSakDto,
		sprak: String,
		vedleggsnrListe: List<String>
	): DokumentSoknadDto {
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
					arkivertSoknad.innsendingsId
						?: innsendingsId, // har ikke referanse til tidligere innsendt søknad, bruker søknadens egen innsendingsId istedenfor
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

			val innsendtVedleggsnrListe: List<String> =
				arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != arkivertSoknad.skjemanr }.map { it.vedleggsnr }
			// Opprett vedlegg til ettersendingssøknaden gitt spesifiserte skjemanr som ikke er funnet i nyeste relaterte arkiverte søknad.
			val vedleggDbDataListe = vedleggService.opprettVedleggTilSoknad(
				savedSoknadDbData.id!!,
				vedleggsnrListe.filter { !innsendtVedleggsnrListe.contains(it) },
				sprak
			)
			// Opprett vedlegg til ettersendingssøknad gitt vedlegg i nyeste arkiverte søknad for spesifisert skjemanummer
			val innsendtVedleggDbDataListe =
				vedleggService.opprettInnsendteVedleggTilSoknad(savedSoknadDbData.id, arkivertSoknad)
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
	fun opprettNySoknad(dokumentSoknadDto: DokumentSoknadDto): SkjemaDto {
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
			return mapTilSkjemaDto(savedDokumentSoknadDto)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, dokumentSoknadDto.tema)
			throw e
		}
	}

	fun hentAktiveSoknader(brukerIds: List<String>): List<DokumentSoknadDto> {
		return brukerIds.flatMap { hentSoknadGittBrukerId(it) }
	}

	fun hentInnsendteSoknader(brukerIds: List<String>): List<DokumentSoknadDto> {
		return brukerIds.flatMap { hentSoknadGittBrukerId(it, SoknadsStatus.Innsendt) }
	}

	private fun hentSoknadGittBrukerId(
		brukerId: String,
		soknadsStatus: SoknadsStatus = SoknadsStatus.Opprettet
	): List<DokumentSoknadDto> {
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
		if (!dokumentSoknadDto.kanGjoreEndringer)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den er innsendt eller slettet"
			)

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { vedleggService.slettVedleggOgDensFiler(it) }
		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		repo.slettSoknad(dokumentSoknadDto, HendelseType.SlettetPermanentAvBruker)

		val soknadDbData =
			mapTilSoknadDb(dokumentSoknadDto, dokumentSoknadDto.innsendingsId!!, SoknadsStatus.SlettetAvBruker)
		val slettetSoknad = lagDokumentSoknadDto(
			soknadDbData,
			dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!) })
		publiserBrukernotifikasjon(slettetSoknad)
		innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAutomatisk(innsendingsId: String) {
		val operation = InnsenderOperation.SLETT.name

		// Ved automatisk sletting beholdes innslag i basen, men eventuelt opplastede filer slettes
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (!dokumentSoknadDto.kanGjoreEndringer)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den allerede er innsendt"
			)

		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettFilerForVedlegg(it.id!!) }
		val slettetSoknadDb =
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.AutomatiskSlettet))

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
		repo.slettSoknad(dokumentSoknadDto, HendelseType.SlettetPermanentAvSystem)

		logger.info("$innsendingsId: opprettet:${dokumentSoknadDto.opprettetDato}, status: ${dokumentSoknadDto.status} er permanent slettet")

		innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
	}

	fun finnOgSlettArkiverteSoknader(dagerGamle: Long, vindu: Long) {
		val arkiverteSoknader =
			repo.findAllSoknadBySoknadsstatusAndArkiveringsstatusAndBetweenInnsendtdatos(dagerGamle, vindu)

		arkiverteSoknader.forEach { slettSoknadPermanent(it.innsendingsid) }

	}

	fun slettGamleSoknader(dagerGamle: Long, permanent: Boolean = false) {
		val slettFor = mapTilOffsetDateTime(LocalDateTime.now(), -dagerGamle)
		logger.info("Finn opprettede søknader opprettet før $slettFor permanent=$permanent")
		if (permanent) {
			val soknadDbDataListe = repo.findAllByOpprettetdatoBefore(slettFor)
			logger.info("SlettPermanentSoknader: Funnet ${soknadDbDataListe.size} søknader som skal permanent slettes")
			soknadDbDataListe.forEach { slettSoknadPermanent(it.innsendingsid) }
		} else {
			val soknadDbDataListe = repo.findAllByStatusesAndWithOpprettetdatoBefore(
				listOf(
					SoknadsStatus.Opprettet.name,
					SoknadsStatus.Utfylt.name
				), slettFor
			)

			logger.info("SlettGamleIkkeInnsendteSoknader: Funnet ${soknadDbDataListe.size} søknader som skal slettes")
			soknadDbDataListe.forEach { slettSoknadAutomatisk(it.innsendingsid) }
		}
	}

	fun sjekkHarAlleredeSoknadUnderArbeid(brukerId: String, skjemanr: String, ettersending: Boolean) {
		val aktiveSoknaderGittSkjemanr =
			hentAktiveSoknader(listOf(brukerId)).filter { it.skjemanr == skjemanr && erEttersending(it) == ettersending }

		if (aktiveSoknaderGittSkjemanr.isNotEmpty()) {
			logger.warn("Dupliserer søknad på skjemanr=$skjemanr, søker har allerede ${aktiveSoknaderGittSkjemanr.size} under arbeid")
		}
	}

	fun oppdaterSoknad(innsendingsId: String, dokumentSoknadDto: DokumentSoknadDto): SkjemaDto {
		if (dokumentSoknadDto.vedleggsListe.size != 2) {
			throw BackendErrorException(
				"Feil antall vedlegg. Skal kun ha hoveddokument og hoveddokumentVariant",
				"Innsendt vedleggsliste skal være tom"
			)
		}

		val eksisterendeSoknad = hentSoknad(innsendingsId)
		oppdaterSoknad(eksisterendeSoknad, dokumentSoknadDto, SoknadsStatus.Opprettet)

		val oppdatertDokumentSoknadDto = hentSoknad(innsendingsId)
		return mapTilSkjemaDto(oppdatertDokumentSoknadDto)
	}

	fun oppdaterUtfyltSoknad(innsendingsId: String, dokumentSoknadDto: DokumentSoknadDto): SkjemaDto {
		val eksisterendeSoknad = hentSoknad(innsendingsId)
		oppdaterSoknad(eksisterendeSoknad, dokumentSoknadDto, SoknadsStatus.Utfylt)

		vedleggService.slettEksisterendeVedleggVedOppdatering(eksisterendeSoknad.vedleggsListe, dokumentSoknadDto)

		val oppdatertDokumentSoknadDto = hentSoknad(innsendingsId)
		return mapTilSkjemaDto(oppdatertDokumentSoknadDto)
	}

	fun oppdaterSoknad(
		eksisterendeSoknad: DokumentSoknadDto,
		dokumentSoknadDto: DokumentSoknadDto,
		status: SoknadsStatus
	) {
		// Valider søknaden mot eksisterende søknad ved å sjekke felter som ikke er lov til å oppdatere
		validerInnsendtSoknadMotEksisterende(dokumentSoknadDto, eksisterendeSoknad)

		// Oppdater søknaden
		val soknadDb = mapTilSoknadDb(
			dokumentSoknadDto = dokumentSoknadDto,
			innsendingsId = eksisterendeSoknad.innsendingsId!!,
			id = eksisterendeSoknad.id,
			status = status
		)
		val oppdatertSoknad = repo.lagreSoknad(soknadDb)
		val soknadsId = oppdatertSoknad.id!!

		// Oppdater vedlegg
		dokumentSoknadDto.vedleggsListe.forEach { nyttVedlegg ->
			vedleggService.lagreVedleggVedOppdatering(eksisterendeSoknad, nyttVedlegg, soknadsId)
		}

		logger.info("Oppdatert søknad for innsendingsId: {}", eksisterendeSoknad.innsendingsId)
	}


	fun validerInnsendtSoknadMotEksisterende(innsendtSoknad: DokumentSoknadDto, eksisterendeSoknad: DokumentSoknadDto) {
		innsendtSoknad.validerSoknadVedOppdatering(eksisterendeSoknad)
		innsendtSoknad.validerVedleggsListeVedOppdatering(eksisterendeSoknad)
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

}
