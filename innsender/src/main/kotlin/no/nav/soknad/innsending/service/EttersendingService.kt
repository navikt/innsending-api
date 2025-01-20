package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.kodeverk.KodeverkType.*
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.KVITTERINGS_NR
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.innsending.util.mapping.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Service
class EttersendingService(
	private val repo: RepositoryUtils,
	private val innsenderMetrics: InnsenderMetrics,
	private val skjemaService: SkjemaService,
	private val brukerNotifikasjon: BrukernotifikasjonPublisher,
	private val exceptionHelper: ExceptionHelper,
	private val vedleggService: VedleggService,
	private val soknadService: SoknadService,
	private val safService: SafService,
	private val tilgangskontroll: Tilgangskontroll,
	private val kodeverkService: KodeverkService,
	private val subjectHandler: SubjectHandlerInterface
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	// Lagre ettersendingssøknad i DB
	@Transactional
	fun saveEttersending(
		brukerId: String,
		ettersendingsId: String?,
		tittel: String,
		skjemanr: String,
		tema: String,
		sprak: String,
		forsteInnsendingsDato: OffsetDateTime,
		fristForEttersendelse: Long? = Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
	)
		: SoknadDbData {
		val innsendingsId = Utilities.laginnsendingsId()
		val applikasjon = subjectHandler.getClientId()

		// lagre soknad
		return repo.lagreSoknad(
			SoknadDbData(
				id = null,
				innsendingsid = innsendingsId,
				tittel = tittel,
				skjemanr = skjemanr,
				tema = tema,
				spraak = finnSpraakFraInput(sprak),
				status = SoknadsStatus.Opprettet,
				brukerid = brukerId,
				ettersendingsid = ettersendingsId ?: innsendingsId,
				opprettetdato = LocalDateTime.now(),
				endretdato = LocalDateTime.now(),
				innsendtdato = null,
				visningssteg = 0,
				visningstype = VisningsType.ettersending,
				forsteinnsendingsdato = mapTilLocalDateTime(forsteInnsendingsDato),
				ettersendingsfrist = fristForEttersendelse,
				arkiveringsstatus = ArkiveringsStatus.IkkeSatt,
				applikasjon = applikasjon,
				skalslettesdato = OffsetDateTime.now().plusDays(Constants.DEFAULT_LEVETID_OPPRETTET_SOKNAD)
			)
		)
	}

	@Transactional
	fun saveEttersending(
		nyesteSoknad: DokumentSoknadDto,
		ettersendingsId: String,
		erSystemGenerert: Boolean = false
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name
		try {
			logger.debug("Skal opprette ettersendingssøknad basert på ${nyesteSoknad.innsendingsId} med ettersendingsid=$ettersendingsId. " +
				"Status for vedleggene til original søknad ${
					nyesteSoknad.vedleggsListe.map
					{
						it.vedleggsnr + ':' + it.opplastingsStatus + ':' + mapTilLocalDateTime(it.innsendtdato) + ':' + mapTilLocalDateTime(
							it.opprettetdato
						)
					}
				}"
			)
			// Lagre ettersendingssøknad i DB
			val savedEttersendingsSoknad = saveEttersending(
				brukerId = nyesteSoknad.brukerId,
				ettersendingsId = ettersendingsId,
				tittel = nyesteSoknad.tittel,
				skjemanr = nyesteSoknad.skjemanr,
				tema = nyesteSoknad.tema,
				sprak = nyesteSoknad.spraak!!,
				forsteInnsendingsDato = nyesteSoknad.forsteInnsendingsDato ?: nyesteSoknad.innsendtDato
				?: nyesteSoknad.endretDato ?: nyesteSoknad.opprettetDato,
				nyesteSoknad.fristForEttersendelse
			)

			// Lagre vedlegg i DB
			val vedleggDbDataListe = nyesteSoknad.vedleggsListe
				.filter { !(it.erHoveddokument || it.vedleggsnr == KVITTERINGS_NR) }
				.map { v ->
					val savedVedleggDb = repo.lagreVedlegg(
						VedleggDbData(
							id = null,
							soknadsid = savedEttersendingsSoknad.id!!,
							status = if (OpplastingsStatusDto.SendSenere == v.opplastingsStatus)
								OpplastingsStatus.IKKE_VALGT else mapTilDbOpplastingsStatus(v.opplastingsStatus),
							erhoveddokument = v.erHoveddokument,
							ervariant = v.erVariant,
							erpdfa = v.erPdfa,
							erpakrevd = v.erPakrevd,
							vedleggsnr = v.vedleggsnr,
							tittel = v.tittel,
							label = v.label,
							beskrivelse = v.beskrivelse,
							mimetype = mapTilDbMimetype(v.mimetype),
							uuid = UUID.randomUUID().toString(),
							opprettetdato = v.opprettetdato.toLocalDateTime(),
							endretdato = LocalDateTime.now(),
							innsendtdato = if (v.opplastingsStatus == OpplastingsStatusDto.Innsendt && v.innsendtdato == null)
								nyesteSoknad.innsendtDato?.toLocalDateTime() else v.innsendtdato?.toLocalDateTime(),
							vedleggsurl = if (v.vedleggsnr != null)
								skjemaService.hentSkjema(v.vedleggsnr!!, nyesteSoknad.spraak ?: "nb", false).url else null,
							formioid = v.formioId,
							opplastingsvalgkommentarledetekst =  v.opplastingsValgKommentarLedetekst,
							opplastingsvalgkommentar = null
						)
					)

					savedVedleggDb
				}

			// Publiser brukernotifikasjon
			val dokumentSoknadDto = lagDokumentSoknadDto(savedEttersendingsSoknad, vedleggDbDataListe, erSystemGenerert)
			publiserBrukernotifikasjon(dokumentSoknadDto)

			// Logg og metrics
			innsenderMetrics.incOperationsCounter(operation, dokumentSoknadDto.tema)
			innsenderMetrics.incOperationsCounter(operation, nyesteSoknad.tema)
			logger.debug("Opprettet ${dokumentSoknadDto.innsendingsId} basert på ${nyesteSoknad.innsendingsId} med ettersendingsid=$ettersendingsId. " +
				"Med vedleggsstatus ${
					dokumentSoknadDto.vedleggsListe.map {
						it.vedleggsnr + ':' + it.opplastingsStatus + ':' + mapTilLocalDateTime(
							it.innsendtdato
						)
					}
				}"
			)

			return dokumentSoknadDto
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, nyesteSoknad.tema)
			throw e
		}
	}

	@Transactional
	fun createEttersendingFromInnsendtSoknad(
		brukerId: String,
		existingSoknad: DokumentSoknadDto,
		ettersending: OpprettEttersending,
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name
		val vedleggsnrList = ettersending.vedleggsListe?.map { it.vedleggsnr } ?: emptyList()

		try {
			logger.info("Oppretter ettersending fra innsendt søknad fra ${existingSoknad.innsendingsId} og vedleggsliste = $vedleggsnrList")
			val ettersendingDb = saveEttersending(
				brukerId = brukerId,
				ettersendingsId = existingSoknad.ettersendingsId ?: existingSoknad.innsendingsId!!,
				tittel = ettersending.tittel ?: existingSoknad.tittel,
				skjemanr = ettersending.skjemanr,
				tema = ettersending.tema,
				sprak = ettersending.sprak,
				forsteInnsendingsDato = existingSoknad.forsteInnsendingsDato ?: existingSoknad.innsendtDato
				?: existingSoknad.endretDato ?: existingSoknad.opprettetDato,
				fristForEttersendelse = existingSoknad.fristForEttersendelse
			)

			val combinedVedleggList =
				vedleggService.saveVedleggWithExistingData(
					soknadsId = ettersendingDb.id!!,
					vedleggList = ettersending.vedleggsListe ?: emptyList(),
					existingSoknad = existingSoknad
				)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return lagDokumentSoknadDto(ettersendingDb, combinedVedleggList)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, existingSoknad.tema)
			throw e
		} finally {
			innsenderMetrics.incOperationsCounter(operation, existingSoknad.tema)
		}
	}

	@Transactional
	fun createEttersendingFromArchivedSoknad(
		brukerId: String,
		archivedSoknad: AktivSakDto,
		ettersending: OpprettEttersending,
		forsteInnsendingsDato: OffsetDateTime?,
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		logger.info("Oppretter ettersending fra arkivert søknad fra ${archivedSoknad.innsendingsId} for skjemanr=${archivedSoknad.skjemanr}")
		try {
			val ettersendingDb = saveEttersending(
				brukerId = brukerId,
				ettersendingsId = archivedSoknad.innsendingsId,
				tittel = ettersending.tittel ?: archivedSoknad.tittel,
				skjemanr = ettersending.skjemanr,
				tema = ettersending.tema,
				sprak = ettersending.sprak,
				forsteInnsendingsDato = forsteInnsendingsDato ?: archivedSoknad.innsendtDato
			)

			val combinedVedleggList =
				vedleggService.saveVedleggWithArchivedData(
					soknadsId = ettersendingDb.id!!,
					vedleggList = ettersending.vedleggsListe ?: emptyList(),
					archivedSoknad = archivedSoknad
				)
			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return lagDokumentSoknadDto(ettersendingDb, combinedVedleggList)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, archivedSoknad.tema)
			throw e
		} finally {
			innsenderMetrics.incOperationsCounter(operation, archivedSoknad.tema)
		}
	}

	@Transactional
	fun createEttersending(brukerId: String, ettersending: OpprettEttersending): DokumentSoknadDto {
		logger.info("Oppretter ettersending for skjemanr=${ettersending.skjemanr}")
		val operation = InnsenderOperation.OPPRETT.name

		try {
			// lagre soknad
			val ettersendingsSoknadDb = saveEttersending(
				brukerId = brukerId,
				ettersendingsId = null,
				tittel = ettersending.tittel ?: "",
				skjemanr = ettersending.skjemanr,
				tema = ettersending.tema,
				sprak = ettersending.sprak,
				forsteInnsendingsDato = OffsetDateTime.now()
			)

			val vedleggDbDataListe = vedleggService.saveVedlegg(
				soknadsId = ettersendingsSoknadDb.id!!,
				vedleggList = ettersending.vedleggsListe ?: emptyList(),
			)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return lagDokumentSoknadDto(ettersendingsSoknadDb, vedleggDbDataListe, false)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, ettersending.tema)
			throw e
		} finally {
			innsenderMetrics.incOperationsCounter(operation, ettersending.tema)
		}
	}

	// Creates ettersending if required documents are missing
	fun sjekkOgOpprettEttersendingsSoknad(
		innsendtSoknadDto: DokumentSoknadDto,
		manglende: List<VedleggDto>,
		soknadDtoInput: DokumentSoknadDto
	) {
		logger.info(
			"${innsendtSoknadDto.innsendingsId}: antall vedlegg som skal ettersendes " +
				"${innsendtSoknadDto.vedleggsListe.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.SendSenere }.size}"
		)

		// Det mangler vedlegg så det opprettes en ettersendingssøknad av systemet
		// Dagpenger (DAG) har sin egen løsning for å opprette ettersendingssøknader
		if (manglende.isNotEmpty() && !"DAG".equals(innsendtSoknadDto.tema, true)) {
			logger.info("${soknadDtoInput.innsendingsId}: Skal opprette ettersendingssoknad")
			saveEttersending(
				nyesteSoknad = innsendtSoknadDto,
				ettersendingsId = innsendtSoknadDto.ettersendingsId ?: innsendtSoknadDto.innsendingsId!!,
				erSystemGenerert = true
			)
		}
	}

	fun getArkiverteEttersendinger(
		skjemanr: String,
		brukerId: String
	): List<AktivSakDto> {
		return try {
			safService.hentInnsendteSoknader(brukerId)
				.filter { skjemanr == it.skjemanr && it.innsendingsId != null }
				.filter { it.innsendtDato.isAfter(OffsetDateTime.now().minusDays(Constants.MAX_AKTIVE_DAGER)) }
				.sortedByDescending { it.innsendtDato }
		} catch (e: Exception) {
			logger.info("Ingen søknader funnet i basen for bruker på skjemanr = $skjemanr")
			emptyList()
		}
	}

	fun getInnsendteSoknader(skjemanr: String): List<DokumentSoknadDto> {
		return try {
			soknadService.hentInnsendteSoknader(tilgangskontroll.hentPersonIdents())
				.filter { it.skjemanr == skjemanr }
				.filter { it.innsendtDato!!.isAfter(OffsetDateTime.now().minusDays(Constants.MAX_AKTIVE_DAGER)) }
				.sortedByDescending { it.innsendtDato }
		} catch (e: Exception) {
			logger.info("Ingen søknader funnet i basen for bruker på skjemanr = $skjemanr")
			emptyList()
		}
	}

	@Transactional
	fun createEttersendingFromExternalApplication(
		brukerId: String,
		eksternOpprettEttersending: EksternOpprettEttersending,
		erNavInitiert: Boolean = false
	): DokumentSoknadDto {
		val mappedEttersending = mapToOpprettEttersending(eksternOpprettEttersending)

		kodeverkService.validateEttersending(
			ettersending = mappedEttersending,
			kodeverkTypes = listOf(KODEVERK_NAVSKJEMA, KODEVERK_TEMA, KODEVERK_VEDLEGGSKODER)
		)

		val enrichedEttersending = kodeverkService.enrichEttersendingWithKodeverkInfo(mappedEttersending)

		// Create ettersending based on existing søknad or create a new one
		val dokumentSoknadDto =
			if (eksternOpprettEttersending.koblesTilEksisterendeSoknad == true) {
				createEttersendingFromExistingSoknader(brukerId = brukerId, ettersending = enrichedEttersending)
			} else {
				createEttersending(brukerId = brukerId, ettersending = enrichedEttersending)
			}

		publiserBrukernotifikasjon(dokumentSoknadDto, eksternOpprettEttersending.brukernotifikasjonstype, erNavInitiert)

		return dokumentSoknadDto

	}

	fun createEttersendingFromFyllutEttersending(
		brukerId: String,
		ettersending: OpprettEttersending,
	): DokumentSoknadDto {
		val dokumentSoknadDto = createEttersendingFromExistingSoknader(
			brukerId = brukerId,
			ettersending = ettersending
		)
		publiserBrukernotifikasjon(dokumentSoknadDto)
		return dokumentSoknadDto
	}

	// Create an ettersending based on previous soknader (from db or JOARK)
	fun createEttersendingFromExistingSoknader(
		brukerId: String,
		ettersending: OpprettEttersending,
	): DokumentSoknadDto {
		val innsendteSoknader = getInnsendteSoknader(ettersending.skjemanr)
		val arkiverteSoknader = getArkiverteEttersendinger(ettersending.skjemanr, brukerId)

		return createEttersendingFromExistingSoknader(
			innsendteSoknader = innsendteSoknader,
			arkiverteSoknader = arkiverteSoknader,
			brukerId = brukerId,
			ettersending = ettersending
		)
	}

	// Get info from Sanity before creating an ettersending
	fun createEttersendingFromExistingSoknaderUsingSanity(
		brukerId: String,
		opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr
	): DokumentSoknadDto {
		val vedleggList = opprettEttersendingGittSkjemaNr.vedleggsListe ?: emptyList()
		val skjemanr = opprettEttersendingGittSkjemaNr.skjemanr
		val sprak = finnSpraakFraInput(opprettEttersendingGittSkjemaNr.sprak)

		val kodeverkSkjema = skjemaService.hentSkjema(skjemanr, sprak)

		val ettersending = OpprettEttersending(
			tittel = kodeverkSkjema.tittel ?: "",
			skjemanr = skjemanr,
			sprak = sprak,
			tema = kodeverkSkjema.tema ?: "",
			vedleggsListe = vedleggService.enrichVedleggListFromSanity(vedleggList, sprak)
		)

		val dokumentSoknadDto = createEttersendingFromExistingSoknader(brukerId, ettersending)
		publiserBrukernotifikasjon(dokumentSoknadDto)

		return dokumentSoknadDto
	}

	private fun createEttersendingFromExistingSoknader(
		innsendteSoknader: List<DokumentSoknadDto>,
		arkiverteSoknader: List<AktivSakDto>,
		brukerId: String,
		ettersending: OpprettEttersending,
	): DokumentSoknadDto {
		// Create a new ettersending without connecting it to an existing søknad
		if (innsendteSoknader.isEmpty() && arkiverteSoknader.isEmpty()) {
			return createEttersending(brukerId = brukerId, ettersending = ettersending)
		}

		// Create a new ettersending based on the latest innsendt søknad
		if (innsendteSoknader.isNotEmpty()) {
			if (arkiverteSoknader.isEmpty() ||
				innsendteSoknader[0].innsendingsId == arkiverteSoknader[0].innsendingsId ||
				innsendteSoknader[0].innsendtDato!!.isAfter(arkiverteSoknader[0].innsendtDato)
			) {
				return createEttersendingFromInnsendtSoknad(
					brukerId = brukerId,
					existingSoknad = innsendteSoknader[0],
					ettersending = ettersending,
				)
			} else {
				return createEttersendingFromArchivedSoknad(
					brukerId = brukerId,
					archivedSoknad = arkiverteSoknader[0],
					ettersending = ettersending,
					forsteInnsendingsDato = innsendteSoknader[0].forsteInnsendingsDato,
				)
			}
		}
		// Create a new ettersending based on the latest archived søknad + additional vedlegg from input
		return createEttersendingFromArchivedSoknad(
			brukerId = brukerId,
			archivedSoknad = arkiverteSoknader[0],
			ettersending = ettersending,
			forsteInnsendingsDato = arkiverteSoknader[0].innsendtDato,
		)
	}


	fun logWarningForExistingEttersendelse(brukerId: String, skjemanr: String) {
		val aktiveSoknaderGittSkjemanr = soknadService.hentAktiveSoknader(brukerId, skjemanr, SoknadType.ettersendelse)
		if (aktiveSoknaderGittSkjemanr.isNotEmpty()) {
			logger.warn("Dupliserer søknad på skjemanr=$skjemanr, søker har allerede ${aktiveSoknaderGittSkjemanr.size} under arbeid")
		}
	}

	private fun publiserBrukernotifikasjon(
		dokumentSoknadDto: DokumentSoknadDto,
		brukernotifikasjonstype: BrukernotifikasjonsType? = BrukernotifikasjonsType.utkast,
		erNavInitiert: Boolean = false
	): Boolean = try {
		if (brukernotifikasjonstype == BrukernotifikasjonsType.oppgave) {
			brukerNotifikasjon.soknadStatusChange(dokumentSoknadDto.copy(erSystemGenerert = true), erNavInitiert)
		} else {
			brukerNotifikasjon.soknadStatusChange(dokumentSoknadDto, erNavInitiert)
		}
	} catch (e: Exception) {
		throw BackendErrorException("Feil i ved avslutning av brukernotifikasjon for søknad ${dokumentSoknadDto.tittel}", e)
	}
}
