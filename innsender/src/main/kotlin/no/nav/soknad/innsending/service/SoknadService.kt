package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.FilDbData
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.fillager.FillagerNamespace
import no.nav.soknad.innsending.service.fillager.FillagerService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD
import no.nav.soknad.innsending.util.Constants.TRANSACTION_TIMEOUT
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.mapping.*
import no.nav.soknad.innsending.util.models.hovedDokumentVariant
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
	private val filService: FilService,
	private val fillagerService: FillagerService,
	private val innsenderMetrics: InnsenderMetrics,
	private val exceptionHelper: ExceptionHelper,
	private val subjectHandler: SubjectHandlerInterface
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Deprecated("Is currently only used in tests, will be removed in the future")
	@Transactional(timeout= TRANSACTION_TIMEOUT)
	fun opprettSoknad(
		brukerId: String,
		skjemanr: String,
		spraak: String,
		vedleggsnrListe: List<String> = emptyList()
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		// hentSkjema informasjon gitt skjemanr
		val kodeverkSkjema = skjemaService.hentSkjema(skjemanr, spraak)
		val applikasjon = subjectHandler.getClientId()

		try {
			// lagre soknad
			val savedSoknadDbData = repo.lagreSoknad(
				SoknadDbData(
					id = null,
					innsendingsid = Utilities.laginnsendingsId(),
					tittel = kodeverkSkjema.tittel ?: "",
					skjemanr = kodeverkSkjema.skjemanummer ?: "",
					tema = kodeverkSkjema.tema ?: "",
					spraak = spraak,
					status = SoknadsStatus.Opprettet,
					brukerid = brukerId,
					ettersendingsid = null,
					opprettetdato = LocalDateTime.now(),
					endretdato = LocalDateTime.now(),
					innsendtdato = null,
					visningssteg = 0,
					visningstype = VisningsType.fyllUt,
					kanlasteoppannet = true,
					forsteinnsendingsdato = null,
					ettersendingsfrist = Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE,
					arkiveringsstatus = ArkiveringsStatus.IkkeSatt,
					applikasjon = applikasjon,
					skalslettesdato = OffsetDateTime.now().plusDays(DEFAULT_LEVETID_OPPRETTET_SOKNAD),
					ernavopprettet = false
				)
			)

			// Lagre soknadens hovedvedlegg
			val skjemaDbData = vedleggService.opprettHovedddokumentVedlegg(savedSoknadDbData, kodeverkSkjema)

			val vedleggDbDataListe = vedleggService.saveVedlegg(savedSoknadDbData.id!!, vedleggsnrListe, spraak)

			val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe

			val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)

			return dokumentSoknadDto
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, kodeverkSkjema.tema ?: "Ukjent")
			throw e
		} finally {
			innsenderMetrics.incOperationsCounter(operation, kodeverkSkjema.tema ?: "Ukjent")
		}
	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun opprettNySoknad(dokumentSoknadDto: DokumentSoknadDto): SkjemaDto {
		val operation = InnsenderOperation.OPPRETT.name

		val innsendingsId = Utilities.laginnsendingsId()
		try {
			val savedSoknadDbData = repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId))
			val soknadsid = savedSoknadDbData.id
			val savedVedleggDbData = vedleggService.saveVedleggFromDto(soknadsid!!, dokumentSoknadDto.vedleggsListe)

			val savedDokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbData)
			// lagre mottatte filer i fil tabellen.
			lagreFiler(savedDokumentSoknadDto, dokumentSoknadDto)

			innsenderMetrics.incOperationsCounter(operation, dokumentSoknadDto.tema)
			return mapTilSkjemaDto(savedDokumentSoknadDto)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, dokumentSoknadDto.tema)
			throw e
		}
	}


	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun lagreUinnloggetSoknad(uinnloggetSoknadDto: NologinSoknadDto): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			input = uinnloggetSoknadDto.soknadDto,
			existingSoknad = null,
			brukerId = uinnloggetSoknadDto.soknadDto.brukerId,
			applikasjon =  subjectHandler.getClientId(),
			visningsType = VisningsType.nologin
		)
		val innsendingsId = dokumentSoknadDto.innsendingsId!!
		try {
			val savedSoknadDbData = repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId))
			val soknadsid = savedSoknadDbData.id
			val savedVedleggDbData = vedleggService.saveVedleggFromDto(soknadsid!!, dokumentSoknadDto.vedleggsListe, uinnloggetSoknadDto.nologinVedleggList)

			val savedDokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbData)

			// lagre hovedkdokument (PDF) og hoveddokumentvariant (JSON)
			val filDtos = savedDokumentSoknadDto.vedleggsListe
				.filter {it.erHoveddokument}
				.forEach { filService.lagreFil(
					soknadDto = savedDokumentSoknadDto,
					FilDto(vedleggsid = it.id!!, id = null, filnavn = it.vedleggsnr, mimetype = it.mimetype,
						storrelse = it.document?.size, antallsider = null,
						data = if (!it.erVariant) uinnloggetSoknadDto.soknadDto.hoveddokument.document else uinnloggetSoknadDto.soknadDto.hoveddokumentVariant.document,
						opprettetdato = it.opprettetdato)
				)
				}

			// flytte opplastede vedleggsfiler i fillager til fil tabellen.
			lagreFiler(savedDokumentSoknadDto, uinnloggetSoknadDto)

			innsenderMetrics.incOperationsCounter(operation, dokumentSoknadDto.tema)
			return hentSoknad(innsendingsId)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, dokumentSoknadDto.tema)
			throw e
		}
	}

	fun hentAktiveSoknader(brukerIds: List<String>): List<DokumentSoknadDto> {
		return brukerIds.flatMap {
			hentSoknadGittBrukerId(it, SoknadsStatus.Opprettet) + hentSoknadGittBrukerId(it, SoknadsStatus.Utfylt)
		}
	}

	fun hentAktiveSoknader(brukerId: String, skjemanr: String): List<DokumentSoknadDto> {
		return hentAktiveSoknader(listOf(brukerId)).filter { it.skjemanr == skjemanr && it.visningsType !== VisningsType.dokumentinnsending }
	}

	fun hentAktiveSoknader(brukerId: String, skjemanr: String, vararg soknadTyper: SoknadType): List<DokumentSoknadDto> {
		return hentAktiveSoknader(listOf(brukerId)).filter {
			it.skjemanr == skjemanr && (soknadTyper.isEmpty() || soknadTyper.contains(it.soknadstype)) && it.visningsType !== VisningsType.dokumentinnsending
		}
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
		val soknadDbData = repo.hentSoknadDb(id)
		return vedleggService.hentAlleVedlegg(soknadDbData, id.toString())
	}

	// Hent soknad gitt innsendingsid med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(innsendingsId: String): DokumentSoknadDto {
		val soknadDbData = repo.hentSoknadDb(innsendingsId)
		return vedleggService.hentAlleVedlegg(soknadDbData, innsendingsId)
	}

	fun hentSoknadMedHoveddokumentVariant(innsendingsId: String): DokumentSoknadDto {
		val soknadDbData = repo.hentSoknadDb(innsendingsId)

		val vedleggDbData = repo.hentAlleVedleggGittSoknadsid(soknadDbData.id!!)

		var hovedDokumentVariantFilerDbData = emptyList<FilDbData>()

		if (vedleggDbData.hovedDokumentVariant != null) {
			hovedDokumentVariantFilerDbData =
				repo.hentFilerTilVedlegg(innsendingsId, vedleggDbData.hovedDokumentVariant?.id!!)
		}

		return mapTilDokumentSoknadDto(soknadDbData, vedleggDbData, hovedDokumentVariantFilerDbData)
	}

	fun endreSoknad(id: Long, visningsSteg: Long) {
		repo.endreSoknadDb(id, visningsSteg)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun slettSoknadAvBruker(dokumentSoknadDto: DokumentSoknadDto) {
		val operation = InnsenderOperation.SLETT.name

		// slett vedlegg og soknad
		if (!dokumentSoknadDto.kanGjoreEndringer)
			throw IllegalActionException("Det kan ikke gjøres endring på en slettet eller innsendt søknad. Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den er innsendt eller slettet")

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { vedleggService.slettVedleggOgDensFiler(it) }
		repo.slettSoknad(dokumentSoknadDto, HendelseType.SlettetPermanentAvBruker)
		innsenderMetrics.incOperationsCounter(operation, dokumentSoknadDto.tema)
	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun deleteSoknadFromExternalApplication(dokumentSoknadDto: DokumentSoknadDto) {
		return slettSoknadAvBruker(dokumentSoknadDto)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun slettSoknadAutomatisk(innsendingsId: String) {
		val operation = InnsenderOperation.SLETT.name

		// Ved automatisk sletting beholdes innslag i basen, men eventuelt opplastede filer slettes
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (!dokumentSoknadDto.kanGjoreEndringer)
			throw IllegalActionException("Det kan ikke gjøres endring på en slettet eller innsendt søknad. Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den allerede er innsendt")

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettFilerForVedlegg(it.id!!) }
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.AutomatiskSlettet))

		logger.info("slettSoknadAutomatisk: Status for søknad $innsendingsId er satt til ${SoknadsStatus.AutomatiskSlettet}")

		innsenderMetrics.incOperationsCounter(operation, dokumentSoknadDto.tema)
	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun slettSoknadPermanent(innsendingsId: String) {

		val dokumentSoknadDto = hentSoknad(innsendingsId)
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettFilerForVedlegg(it.id!!) }
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettVedlegg(it.id!!) }
		repo.slettSoknad(dokumentSoknadDto, HendelseType.SlettetPermanentAvSystem)

		logger.info("$innsendingsId: opprettet:${dokumentSoknadDto.opprettetDato}, status: ${dokumentSoknadDto.status} er permanent slettet")
	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun finnOgSlettArkiverteSoknader(dagerGamle: Long, vindu: Long) {
		val arkiverteSoknader =
			repo.findAllSoknadBySoknadsstatusAndArkiveringsstatusAndBetweenInnsendtdatos(dagerGamle, vindu)

		arkiverteSoknader.forEach { slettSoknadPermanent(it.innsendingsid) }

	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun slettGamleSoknader(
		dagerGamle: Long = DEFAULT_LEVETID_OPPRETTET_SOKNAD,
		permanent: Boolean = false,
	) {
		val slettFor = mapTilOffsetDateTime(LocalDateTime.now(), -dagerGamle)
		logger.info("Finn opprettede søknader opprettet før $slettFor permanent=$permanent")
		if (permanent) {
			val soknadDbDataListe = repo.findAllByOpprettetdatoBefore(slettFor)
			logger.info("SlettPermanentSoknader: Funnet ${soknadDbDataListe.size} søknader som skal permanent slettes")
			soknadDbDataListe.forEach { slettSoknadPermanent(it.innsendingsid) }
		} else {
			val soknadDbDataListe: List<SoknadDbData> =
				repo.findAllByStatusesAndWithOpprettetdatoBefore(
					listOf(
						SoknadsStatus.Opprettet.name,
						SoknadsStatus.Utfylt.name
					), slettFor
				)

			logger.info("SlettGamleIkkeInnsendteSoknader: Funnet ${soknadDbDataListe.size} søknader som skal slettes")
			soknadDbDataListe.forEach { slettSoknadAutomatisk(it.innsendingsid) }
		}
	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun deleteSoknadBeforeCutoffDate(
		cutoffDate: OffsetDateTime
	): List<String> {
		logger.info("Finner søknader som skal slettes før $cutoffDate")

		val soknaderToDelete =
			repo.findAllByStatusesAndWithSkalSlettesDatoBefore(
				listOf(
					SoknadsStatus.Opprettet.name,
					SoknadsStatus.Utfylt.name
				), cutoffDate
			)

		logger.info("Funnet ${soknaderToDelete.size} søknader som skal slettes")
		return soknaderToDelete.map { it.innsendingsid }.onEach { slettSoknadAutomatisk(it) }
	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun updateSoknad(innsendingsId: String, dokumentSoknadDto: DokumentSoknadDto): SkjemaDto {
		if (dokumentSoknadDto.vedleggsListe.size != 2) {
			throw BackendErrorException("Feil antall vedlegg. Skal kun ha hoveddokument og hoveddokumentVariant. Innsendt vedleggsliste skal være tom")
		}

		val existingSoknad = hentSoknad(innsendingsId)
		val updatedDokumentSoknadDto = updateSoknad(existingSoknad, dokumentSoknadDto, SoknadsStatus.Opprettet)

		return mapTilSkjemaDto(updatedDokumentSoknadDto)
	}

	fun lagreFiler(
		oppdatertDokumentSoknadDto: DokumentSoknadDto,
		dokumentSoknadDto: DokumentSoknadDto
	) {
		oppdatertDokumentSoknadDto.vedleggsListe
			.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOpp }
			.forEach { filService.lagreFil(oppdatertDokumentSoknadDto, it, dokumentSoknadDto.vedleggsListe) }
	}


	/*
	Skal flytte filene fra fillager til fil tabellen for de filene som er lastet opp og som skal være med i innsendingen av søknaden.
	Status for alle vedleggene som har filer som er lastet opp og som skal være med i innsendinge skal settes til LastetOpp.
	 */
	fun lagreFiler(
		oppdatertDokumentSoknadDto: DokumentSoknadDto,
		noLoginSoknadDto: NologinSoknadDto
	) {
		val vedleggsStatusMap = mutableMapOf<String, OpplastingsStatusDto>()
		noLoginSoknadDto.nologinVedleggList
			.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOpp }
			.forEach {
				if (it.fileIdList.isNullOrEmpty()) throw IllegalActionException(
					"Vedlegg med id=${it.vedleggRef} har ingen filer som skal lagres i fil tabellen",
					errorCode = ErrorCode.NOT_FOUND
				)
				kopierFilerForVedlegg(
				soknadDto = oppdatertDokumentSoknadDto,
				vedleggsRef = it.vedleggRef,
				it.fileIdList!!)
			}
	}

	fun kopierFilerForVedlegg(soknadDto: DokumentSoknadDto, vedleggsRef: String, opplastedeFiler: List<String> ){
		val vedleggDto = soknadDto.vedleggsListe.find { it.formioId == vedleggsRef }
		if (vedleggDto == null) throw IllegalActionException("Fant ikke vedlegg med id=$vedleggsRef", errorCode = ErrorCode.NOT_FOUND)

		// for hver fil hent og opprett nytt innslag i fil tabellen
		opplastedeFiler.forEach { fileId ->
			val filSomSkalKopieres = fillagerService.hentFil(filId = fileId, soknadDto.innsendingsId!!, namespace= FillagerNamespace.NOLOGIN)
			if (filSomSkalKopieres == null || filSomSkalKopieres.innhold.size == 0) {
				throw IllegalActionException("Fant ikke fil med id=${fileId} for vedlegg med id=$vedleggsRef", errorCode = ErrorCode.NOT_FOUND)
			}

			val filDto = FilDto(
				id = null,
				vedleggsid = vedleggDto.id!!,
				filnavn = filSomSkalKopieres.metadata.filnavn,
				storrelse = filSomSkalKopieres.metadata.storrelse,
				data = filSomSkalKopieres.innhold,
				mimetype = mapTilMimetype(filSomSkalKopieres.metadata.filtype),
				opprettetdato = OffsetDateTime.now(),
			)
			filService.lagreFil(soknadDto, filDto)
		}

	}

	fun saveHoveddokumentFiler(
		oppdatertDokumentSoknadDto: DokumentSoknadDto,
		dokumentSoknadDto: DokumentSoknadDto
	) {
		oppdatertDokumentSoknadDto.vedleggsListe
			.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOpp && it.erHoveddokument }
			.forEach {
				filService.lagreFil(
					savedDokumentSoknadDto = oppdatertDokumentSoknadDto,
					lagretVedleggDto = it,
					innsendtVedleggDtos = dokumentSoknadDto.vedleggsListe
				)
			}
	}


	fun updateUtfyltSoknad(innsendingsId: String, dokumentSoknadDto: DokumentSoknadDto): SkjemaDto {
		val existingSoknad = hentSoknad(innsendingsId)

		vedleggService.updateVedleggStatuses(existingSoknad.vedleggsListe, dokumentSoknadDto)

		val updatedDokumentSoknadDto = updateSoknad(existingSoknad, dokumentSoknadDto, SoknadsStatus.Utfylt)
		return mapTilSkjemaDto(updatedDokumentSoknadDto)
	}

	fun updateSoknad(
		existingSoknad: DokumentSoknadDto,
		dokumentSoknadDto: DokumentSoknadDto,
		status: SoknadsStatus
	): DokumentSoknadDto {
		// Valider søknaden mot eksisterende søknad ved å sjekke felter som ikke er lov til å oppdatere
		validerInnsendtSoknadMotEksisterende(dokumentSoknadDto, existingSoknad)
		logger.info("Validert søknad mot eksisterende søknader")
		val innsendingsId = existingSoknad.innsendingsId!!

		// Update søknad
		val soknadDb = mapTilSoknadDb(
			dokumentSoknadDto = dokumentSoknadDto,
			innsendingsId = innsendingsId,
			id = existingSoknad.id,
			status = status
		)
		val updatedSoknad = repo.lagreSoknad(soknadDb)
		val soknadId = updatedSoknad.id!!

		// Update vedlegg
		dokumentSoknadDto.vedleggsListe.forEach { nyttVedlegg ->
			vedleggService.lagreVedleggVedOppdatering(existingSoknad, nyttVedlegg, soknadId)
		}

		val updatedDokumentSoknadDto = hentSoknad(innsendingsId)
		saveHoveddokumentFiler(updatedDokumentSoknadDto, dokumentSoknadDto)

		logger.info("Oppdatert søknad for innsendingsId: {}", existingSoknad.innsendingsId)

		return updatedDokumentSoknadDto
	}


	fun validerInnsendtSoknadMotEksisterende(innsendtSoknad: DokumentSoknadDto, eksisterendeSoknad: DokumentSoknadDto) {
		innsendtSoknad.validerSoknadVedOppdatering(eksisterendeSoknad)
		innsendtSoknad.validerVedleggsListeVedOppdatering(eksisterendeSoknad)
	}

}
