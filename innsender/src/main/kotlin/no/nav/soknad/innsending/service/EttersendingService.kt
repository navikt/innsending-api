package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.KVITTERINGS_NR
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.innsending.util.mapping.lagDokumentSoknadDto
import no.nav.soknad.innsending.util.mapping.mapTilDbMimetype
import no.nav.soknad.innsending.util.mapping.mapTilDbOpplastingsStatus
import no.nav.soknad.innsending.util.mapping.mapTilLocalDateTime
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
	private val soknadService: SoknadService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	// Lagre ettersendingssøknad i DB
	fun opprettEttersendingsSoknad(
		brukerId: String, ettersendingsId: String?, tittel: String, skjemanr: String, tema: String, sprak: String,
		forsteInnsendingsDato: OffsetDateTime, fristForEttersendelse: Long? = Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
	)
		: SoknadDbData {
		val innsendingsId = Utilities.laginnsendingsId()
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
				arkiveringsstatus = ArkiveringsStatus.IkkeSatt
			)
		)
	}

	fun opprettEttersendingsSoknad(
		nyesteSoknad: DokumentSoknadDto,
		ettersendingsId: String,
		erSystemGenerert: Boolean = false
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name
		try {
			logger.debug("opprettEttersendingsSoknad: Skal opprette ettersendingssøknad basert på ${nyesteSoknad.innsendingsId} med ettersendingsid=$ettersendingsId. " +
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
			val savedEttersendingsSoknad = opprettEttersendingsSoknad(
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
					repo.lagreVedlegg(
						VedleggDbData(
							id = null,
							soknadsid = savedEttersendingsSoknad.id!!,
							status = if (OpplastingsStatusDto.sendSenere == v.opplastingsStatus)
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
							innsendtdato = if (v.opplastingsStatus == OpplastingsStatusDto.innsendt && v.innsendtdato == null)
								nyesteSoknad.innsendtDato?.toLocalDateTime() else v.innsendtdato?.toLocalDateTime(),
							vedleggsurl = if (v.vedleggsnr != null)
								skjemaService.hentSkjema(v.vedleggsnr!!, nyesteSoknad.spraak ?: "nb", false).url else null,
							formioid = v.formioId
						)
					)
				}

			// Publiser brukernotifikasjon
			var dokumentSoknadDto = lagDokumentSoknadDto(savedEttersendingsSoknad, vedleggDbDataListe, erSystemGenerert)
			publiserBrukernotifikasjon(dokumentSoknadDto)

			// Logg og metrics
			innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
			innsenderMetrics.operationsCounterInc(operation, nyesteSoknad.tema)
			logger.debug("opprettEttersendingsSoknad: opprettet ${dokumentSoknadDto.innsendingsId} basert på ${nyesteSoknad.innsendingsId} med ettersendingsid=$ettersendingsId. " +
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
	fun opprettEttersending(brukerId: String, ettersendingsId: String): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		// Skal opprette en soknad basert på status på vedlegg som skal ettersendes.
		// Basere opplastingsstatus på nyeste innsending på ettersendingsId, dvs. nyeste soknad der innsendingsId eller ettersendingsId lik oppgitt ettersendingsId
		// Det skal være mulig å ettersende allerede ettersendte vedlegg på nytt
		val soknadDbDataList = try {
			repo.finnNyesteSoknadGittEttersendingsId(ettersendingsId)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, "Ukjent")
			throw BackendErrorException("Feil ved henting av søknad $ettersendingsId", e)
		}

		if (soknadDbDataList.isEmpty()) {
			exceptionHelper.reportException(Exception("No SoknadDbData found"), operation, "Ukjent")
			throw ResourceNotFoundException("Kan ikke opprette søknad for ettersending. Soknad med id $ettersendingsId som det skal ettersendes data for ble ikke funnet")
		}

		loggWarningVedEksisterendeEttersendelse(
			brukerId,
			soknadDbDataList.first().skjemanr,
		)

		return opprettEttersendingsSoknad(
			vedleggService.hentAlleVedlegg(soknadDbDataList.first()),
			ettersendingsId
		)
	}

	@Transactional
	fun opprettEttersendingGittSoknadOgVedlegg(
		brukerId: String, nyesteSoknad: DokumentSoknadDto, sprak: String, vedleggsnrListe: List<String>
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		try {
			logger.info("opprettEttersendingGittSoknadOgVedlegg fra ${nyesteSoknad.innsendingsId} og vedleggsliste = $vedleggsnrListe")
			val ettersendingsSoknadDb = opprettEttersendingsSoknad(
				brukerId = brukerId,
				ettersendingsId = nyesteSoknad.ettersendingsId ?: nyesteSoknad.innsendingsId!!,
				tittel = nyesteSoknad.tittel,
				skjemanr = nyesteSoknad.skjemanr,
				tema = nyesteSoknad.tema,
				sprak = nyesteSoknad.spraak!!,
				forsteInnsendingsDato = nyesteSoknad.forsteInnsendingsDato ?: nyesteSoknad.innsendtDato
				?: nyesteSoknad.endretDato ?: nyesteSoknad.opprettetDato,
				fristForEttersendelse = nyesteSoknad.fristForEttersendelse
			)

			val nyesteSoknadVedleggsNrListe =
				nyesteSoknad.vedleggsListe.filter { !(it.erHoveddokument || it.vedleggsnr == KVITTERINGS_NR) }
					.map { it.vedleggsnr }
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
	fun opprettEttersendingGittArkivertSoknadOgVedlegg(
		brukerId: String, arkivertSoknad: AktivSakDto, opprettEttersendingGittSkjemaNr: OpprettEttersendingGittSkjemaNr,
		sprak: String?, forsteInnsendingsDato: OffsetDateTime?
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		logger.info("opprettEttersendingGittArkivertSoknadOgVedlegg: for skjemanr=${arkivertSoknad.skjemanr}")
		try {
			val ettersendingsSoknadDb = opprettEttersendingsSoknad(
				brukerId = brukerId,
				ettersendingsId = arkivertSoknad.innsendingsId,
				tittel = arkivertSoknad.tittel,
				skjemanr = arkivertSoknad.skjemanr,
				tema = arkivertSoknad.tema,
				sprak = sprak ?: "nb",
				forsteInnsendingsDato ?: arkivertSoknad.innsendtDato
			)

			val nyesteSoknadVedleggsNrListe =
				arkivertSoknad.innsendtVedleggDtos.filter { !(it.vedleggsnr == arkivertSoknad.skjemanr || it.vedleggsnr == KVITTERINGS_NR) }
					.map { it.vedleggsnr }
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
	fun opprettEttersendingGittArkivertSoknad(
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
				arkivertSoknad.innsendtVedleggDtos.filter { !(it.vedleggsnr == arkivertSoknad.skjemanr || it.vedleggsnr == KVITTERINGS_NR) }
					.map { it.vedleggsnr }
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
	fun opprettEttersendingGittSkjemanr(
		brukerId: String,
		skjemanr: String,
		spraak: String = "nb",
		vedleggsnrListe: List<String> = emptyList()
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name
		logger.info("opprettEttersendingGittSkjemanr: for skjemanr=$skjemanr")

		val kodeverkSkjema = try {
			// hentSkjema informasjon gitt skjemanr
			skjemaService.hentSkjema(skjemanr, finnSpraakFraInput(spraak))
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, "Ukjent")
			throw e
		}

		try {
			// lagre soknad
			val ettersendingsSoknadDb = opprettEttersendingsSoknad(
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

	fun sjekkOgOpprettEttersendingsSoknad(
		innsendtSoknadDto: DokumentSoknadDto,
		manglende: List<VedleggDto>,
		soknadDtoInput: DokumentSoknadDto
	) {
		logger.info(
			"${innsendtSoknadDto.innsendingsId}: antall vedlegg som skal ettersendes " +
				"${innsendtSoknadDto.vedleggsListe.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.sendSenere }.size}"
		)

		// Det mangler vedlegg så det opprettes en ettersendingssøknad av systemet
		// Dagpenger (DAG) har sin egen løsning for å opprette ettersendingssøknader
		if (manglende.isNotEmpty() && !"DAG".equals(innsendtSoknadDto.tema, true)) {
			logger.info("${soknadDtoInput.innsendingsId}: Skal opprette ettersendingssoknad")
			opprettEttersendingsSoknad(
				nyesteSoknad = innsendtSoknadDto,
				ettersendingsId = innsendtSoknadDto.ettersendingsId ?: innsendtSoknadDto.innsendingsId!!,
				erSystemGenerert = true
			)
		}
	}

	fun loggWarningVedEksisterendeEttersendelse(brukerId: String, skjemanr: String) {
		val aktiveSoknaderGittSkjemanr = soknadService.hentAktiveSoknader(brukerId, skjemanr, SoknadType.ettersendelse)
		if (aktiveSoknaderGittSkjemanr.isNotEmpty()) {
			logger.warn("Dupliserer søknad på skjemanr=$skjemanr, søker har allerede ${aktiveSoknaderGittSkjemanr.size} under arbeid")
		}
	}

	private fun publiserBrukernotifikasjon(dokumentSoknadDto: DokumentSoknadDto): Boolean = try {
		brukerNotifikasjon.soknadStatusChange(dokumentSoknadDto)
	} catch (e: Exception) {
		throw BackendErrorException("Feil i ved avslutning av brukernotifikasjon for søknad ${dokumentSoknadDto.tittel}", e)
	}
}
