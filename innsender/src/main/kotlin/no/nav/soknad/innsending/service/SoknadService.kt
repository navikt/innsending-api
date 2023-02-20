package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.exceptions.SanityException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.pdfutilities.PdfGenerator
import no.nav.soknad.pdfutilities.PdfMerger
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
	private val skjemaService: HentSkjemaDataConsumer,
	private val repo: RepositoryUtils,
	private val brukerNotifikasjon: BrukernotifikasjonPublisher,
	private val fillagerAPI: FillagerInterface,
	private val soknadsmottakerAPI: MottakerInterface,
	private val innsenderMetrics: InnsenderMetrics,
	private val pdlInterface: PdlInterface
) {

	@Value("\${ettersendingsfrist}")
	private var ettersendingsfrist: Long = 14

	private val logger = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun opprettSoknad(brukerId: String, skjemanr: String, spraak: String, vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		// hentSkjema informasjon gitt skjemanr
		val kodeverkSkjema = hentSkjema(skjemanr, spraak)

		try {
			// lagre soknad
			val savedSoknadDbData = repo.lagreSoknad(
				SoknadDbData(
					null, Utilities.laginnsendingsId(), kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: "",
					kodeverkSkjema.tema ?: "", spraak, SoknadsStatus.Opprettet, brukerId, null, LocalDateTime.now(),
					LocalDateTime.now(), null, 0, VisningsType.dokumentinnsending, true,
						forsteinnsendingsdato = null, ettersendingsfrist = Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
				)
			)

			// Lagre soknadens hovedvedlegg
			val skjemaDbData = opprettHovedddokumentVedlegg(savedSoknadDbData, kodeverkSkjema)

			val vedleggDbDataListe = opprettVedleggTilSoknad(savedSoknadDbData.id!!, vedleggsnrListe, spraak)

			val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe

			val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			return dokumentSoknadDto
		} catch (e: Exception) {
			reportException(e, operation, kodeverkSkjema.tema ?: "Ukjent")
			throw e
		} finally {
			innsenderMetrics.operationsCounterInc(operation, kodeverkSkjema.tema ?: "Ukjent")
		}
	}

	private fun opprettHovedddokumentVedlegg(
		savedSoknadDbData: SoknadDbData,
		kodeverkSkjema: KodeverkSkjema
	): VedleggDbData {

		return repo.lagreVedlegg(
			VedleggDbData(
				null,
				savedSoknadDbData.id!!,
				OpplastingsStatus.IKKE_VALGT,
				true,
				ervariant = false,
				true,
				erpakrevd = true,
				vedleggsnr = kodeverkSkjema.skjemanummer ?: kodeverkSkjema.vedleggsid,
				tittel = kodeverkSkjema.tittel ?: "",
				label = kodeverkSkjema.tittel ?: "",
				beskrivelse = "",
				mimetype = null,
				uuid = UUID.randomUUID().toString(),
				opprettetdato = LocalDateTime.now(),
				endretdato = LocalDateTime.now(),
				innsendtdato = null,
				vedleggsurl = kodeverkSkjema.url
			)
		)
	}

	private fun opprettVedleggTilSoknad(
		soknadsId: Long,
		vedleggsnrListe: List<String>,
		spraak: String,
		tittel: String? = null
	): List<VedleggDbData> {
		val vedleggDbDataListe = vedleggsnrListe
			.map { nr -> hentSkjema(nr, spraak) }
			.map { v ->
				repo.lagreVedlegg(
					VedleggDbData(
						null, soknadsId, OpplastingsStatus.IKKE_VALGT,
						false, ervariant = false, false, v.skjemanummer != "N6",
						v.skjemanummer, tittel ?: v.tittel ?: "",tittel ?: v.tittel ?: "","",null,
						UUID.randomUUID().toString(), LocalDateTime.now(), LocalDateTime.now(), null, v.url
					)
				)
			}
		return vedleggDbDataListe
	}

	private fun opprettInnsendteVedleggTilSoknad(
		soknadsId: Long,
		arkivertSoknad: AktivSakDto
	): List<VedleggDbData> {
		val vedleggDbDataListe = arkivertSoknad.innsendtVedleggDtos
			.filter { it.vedleggsnr != arkivertSoknad.skjemanr }
			.map { v ->
				repo.lagreVedlegg(
					VedleggDbData(
						null, soknadsId, OpplastingsStatus.INNSENDT,
						false, ervariant = false, erpdfa = true, erpakrevd = true,
						vedleggsnr = v.vedleggsnr, tittel = v.tittel, label = v.tittel, beskrivelse = "", mimetype = null,
						uuid = UUID.randomUUID().toString(),
						opprettetdato = mapTilLocalDateTime(arkivertSoknad.innsendtDato) ?: LocalDateTime.now(),
						endretdato = mapTilLocalDateTime(arkivertSoknad.innsendtDato) ?: LocalDateTime.now(),
						innsendtdato = mapTilLocalDateTime(arkivertSoknad.innsendtDato) ?: LocalDateTime.now(), vedleggsurl = null
					)
				)
			}
		return vedleggDbDataListe
	}

	private fun hentSkjema(nr: String, spraak: String, kastException: Boolean = true) =  try {
		skjemaService.hentSkjemaEllerVedlegg(nr, spraak)
	} catch (re: SanityException) {
		if (kastException) {
			throw ResourceNotFoundException(re.arsak, re.message ?: "", "errorCode.resourceNotFound.schemaNotFound")
		} else {
			logger.warn("Skjemanr=$nr ikke funnet i Sanity. Fortsetter behandling")
			KodeverkSkjema()
		}
	}

	@Transactional
	fun opprettSoknadForEttersendingGittSkjemanr(brukerId: String, skjemanr: String, spraak: String = "nb", vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name
		logger.info("opprettSoknadForEttersendingGittSkjemanr: for skjemanr=$skjemanr")

		val kodeverkSkjema = try {
			// hentSkjema informasjon gitt skjemanr
			hentSkjema(skjemanr, finnSpraakFraInput(spraak))
		} catch (e: Exception) {
			reportException(e, operation, "Ukjent")
			throw e
		}

		try {
			// lagre soknad
			val ettersendingsSoknadDb = opprettEttersendingsSoknad(brukerId = brukerId, ettersendingsId = null,
				kodeverkSkjema.tittel ?: "", skjemanr, kodeverkSkjema.tema ?: "", spraak, OffsetDateTime.now())

			// For hvert vedleggsnr hent definisjonen fra Sanity og lagr vedlegg.
			val vedleggDbDataListe = opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, vedleggsnrListe, spraak, null)

			val dokumentSoknadDto = lagDokumentSoknadDto(ettersendingsSoknadDb, vedleggDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return dokumentSoknadDto
		} catch (e: Exception) {
			reportException(e, operation, kodeverkSkjema.tema ?: "Ukjent")
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
			reportException(e, operation, "Ukjent")
			throw BackendErrorException(e.message, "Feil ved henting av søknad $ettersendingsId", "errorCode.backendError.applicationFetchError")
		}

		if (soknadDbDataList.isEmpty()) {
			reportException(Exception("No SoknadDbData found"), operation, "Ukjent")
			throw ResourceNotFoundException(
				"Kan ikke opprette søknad for ettersending",
				"Soknad med id $ettersendingsId som det skal ettersendes data for ble ikke funnet",
				"errorCode.resourceNotFound.applicationUnknown"
			)
		}

		sjekkHarAlleredeSoknadUnderArbeid(brukerId, soknadDbDataList.first().skjemanr, true)
		return opprettEttersendingsSoknad(hentAlleVedlegg(soknadDbDataList.first()), ettersendingsId)
	}

	@Transactional
	fun opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg(
		brukerId: String, nyesteSoknad: DokumentSoknadDto, sprak: String, vedleggsnrListe: List<String>): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name

		try {
			logger.info("opprettSoknadForettersendingAvVedleggGittSoknadOgVedlegg fra ${nyesteSoknad.innsendingsId} og vedleggsliste = $vedleggsnrListe")
			val ettersendingsSoknadDb = opprettEttersendingsSoknad(brukerId, nyesteSoknad.ettersendingsId ?: nyesteSoknad.innsendingsId!!,
				nyesteSoknad.tittel, nyesteSoknad.skjemanr, nyesteSoknad.tema, nyesteSoknad.spraak!!,
				nyesteSoknad.forsteInnsendingsDato ?: nyesteSoknad.innsendtDato ?: nyesteSoknad.endretDato ?: nyesteSoknad.opprettetDato,
				nyesteSoknad.fristForEttersendelse)

			val nyesteSoknadVedleggsNrListe = nyesteSoknad.vedleggsListe.filter { !it.erHoveddokument }.map { it.vedleggsnr }
			val filtrertVedleggsnrListe = vedleggsnrListe.filter { !nyesteSoknadVedleggsNrListe.contains(it) }

			val vedleggDbDataListe = opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, filtrertVedleggsnrListe, sprak)

			val innsendtDbDataListe = opprettVedleggTilSoknad(ettersendingsSoknadDb, nyesteSoknad.vedleggsListe)

			val dokumentSoknadDto = lagDokumentSoknadDto(ettersendingsSoknadDb, vedleggDbDataListe + innsendtDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return dokumentSoknadDto
		} catch (e: Exception) {
			reportException(e, operation, nyesteSoknad.tema)
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
			val ettersendingsSoknadDb = opprettEttersendingsSoknad(brukerId = brukerId, ettersendingsId = arkivertSoknad.innsendingsId,
				tittel = arkivertSoknad.tittel, skjemanr = arkivertSoknad.skjemanr, tema = arkivertSoknad.tema, sprak = sprak ?: "nb",
				forsteInnsendingsDato ?: arkivertSoknad.innsendtDato)

			val nyesteSoknadVedleggsNrListe = arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != arkivertSoknad.skjemanr }.map { it.vedleggsnr }
			val filtrertVedleggsnrListe = opprettEttersendingGittSkjemaNr.vedleggsListe?.filter { !nyesteSoknadVedleggsNrListe.contains(it) }.orEmpty()

			val vedleggDbDataListe = opprettVedleggTilSoknad(ettersendingsSoknadDb.id!!, filtrertVedleggsnrListe, sprak ?: "nb" )

			val innsendtDbDataListe = opprettVedleggTilSoknad(ettersendingsSoknadDb, arkivertSoknad)

			val dokumentSoknadDto = lagDokumentSoknadDto(ettersendingsSoknadDb, vedleggDbDataListe + innsendtDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return dokumentSoknadDto
		} catch (e: Exception) {
			reportException(e, operation, arkivertSoknad.tema)
			throw e
		} finally {
			innsenderMetrics.operationsCounterInc(operation, arkivertSoknad.tema)
		}
	}


	private fun opprettEttersendingsSoknad(brukerId: String, ettersendingsId: String?, tittel: String, skjemanr: String, tema: String, sprak: String,
																				 forsteInnsendingsDato: OffsetDateTime, fristForEttersendelse: Long ? = Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE )
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
				ettersendingsfrist = fristForEttersendelse
			)
		)
	}

	private fun opprettVedleggTilSoknad(soknadDbData: SoknadDbData,  vedleggsListe: List<VedleggDto>)
		: List<VedleggDbData> {

		return  vedleggsListe
			.filter { !it.erHoveddokument }
			.map { v ->
				repo.lagreVedlegg(
					VedleggDbData(
						id = null,
						soknadsid = soknadDbData.id!!,
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
						innsendtdato = v.innsendtdato?.toLocalDateTime(),
						vedleggsurl = if (v.vedleggsnr != null) hentSkjema(v.vedleggsnr!!, soknadDbData.spraak ?: "nb", false).url else null
					)
				)
			}
	}

	private fun opprettVedleggTilSoknad(soknadDbData: SoknadDbData,  arkivertSoknad: AktivSakDto)
		: List<VedleggDbData> {

		return  arkivertSoknad.innsendtVedleggDtos
			.filter { it.vedleggsnr != soknadDbData.skjemanr }
			.map { v ->
				repo.lagreVedlegg(
					VedleggDbData(
						id = null,
						soknadsid = soknadDbData.id!!,
						status = OpplastingsStatus.INNSENDT,
						erhoveddokument = false,
						ervariant = false,
						erpdfa = true,
						erpakrevd = true,
						vedleggsnr = v.vedleggsnr,
						tittel = v.tittel,
						label = v.tittel,
						beskrivelse = "",
						mimetype = mapTilDbMimetype(Mimetype.applicationSlashPdf),
						uuid = UUID.randomUUID().toString(),
						opprettetdato = arkivertSoknad.innsendtDato.toLocalDateTime(),
						endretdato = LocalDateTime.now(),
						innsendtdato = arkivertSoknad.innsendtDato.toLocalDateTime(),
						vedleggsurl = hentSkjema(v.vedleggsnr, soknadDbData.spraak ?: "nb", false).url
					)
				)
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
					Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE
				)
			)

			val innsendtVedleggsnrListe: List<String> = arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != arkivertSoknad.skjemanr }.map { it.vedleggsnr }
			// Opprett vedlegg til ettersendingssøknaden gitt spesifiserte skjemanr som ikke er funnet i nyeste relaterte arkiverte søknad.
			val vedleggDbDataListe = opprettVedleggTilSoknad(savedSoknadDbData.id!!, vedleggsnrListe.filter { !innsendtVedleggsnrListe.contains(it) }, sprak)
			// Opprett vedlegg til ettersendingssøknad gitt vedlegg i nyeste arkiverte søknad for spesifisert skjemanummer
			val innsendtVedleggDbDataListe = opprettInnsendteVedleggTilSoknad(savedSoknadDbData.id, arkivertSoknad)
			val savedVedleggDbDataListe = vedleggDbDataListe + innsendtVedleggDbDataListe

			val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)
			publiserBrukernotifikasjon(dokumentSoknadDto)

			return dokumentSoknadDto
		} catch (e: Exception) {
			reportException(e, operation, arkivertSoknad.tema)
			throw e
		} finally {
			innsenderMetrics.operationsCounterInc(operation, arkivertSoknad.tema)
		}
	}

	private fun opprettEttersendingsSoknad (
		nyesteSoknad: DokumentSoknadDto,
		ettersendingsId: String
	): DokumentSoknadDto {
		val operation = InnsenderOperation.OPPRETT.name
		try {
			logger.debug("opprettEttersendingsSoknad: Skal opprette ettersendingssøknad basert på ${nyesteSoknad.innsendingsId} med ettersendingsid=$ettersendingsId. " +
				"Status for vedleggene til original søknad ${nyesteSoknad.vedleggsListe.map
				{ it.vedleggsnr+':'+it.opplastingsStatus+':'+mapTilLocalDateTime(it.innsendtdato)+':'+ mapTilLocalDateTime(it.opprettetdato) }}")

			val savedEttersendingsSoknad  = opprettEttersendingsSoknad(brukerId = nyesteSoknad.brukerId, ettersendingsId = ettersendingsId,
				tittel = nyesteSoknad.tittel, skjemanr = nyesteSoknad.skjemanr, tema = nyesteSoknad.tema, sprak = nyesteSoknad.spraak!!,
				forsteInnsendingsDato = nyesteSoknad.forsteInnsendingsDato ?: nyesteSoknad.innsendtDato ?: nyesteSoknad.endretDato ?: nyesteSoknad.opprettetDato, nyesteSoknad.fristForEttersendelse )

			val vedleggDbDataListe = nyesteSoknad.vedleggsListe
				.filter { !it.erHoveddokument }
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
								hentSkjema(v.vedleggsnr!!, nyesteSoknad.spraak ?: "nb", false).url else null
						)
					)
				}

			val dokumentSoknadDto = lagDokumentSoknadDto(savedEttersendingsSoknad, vedleggDbDataListe)
			publiserBrukernotifikasjon(dokumentSoknadDto)

			innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
			logger.debug("opprettEttersendingsSoknad: opprettet ${dokumentSoknadDto.innsendingsId} basert på ${nyesteSoknad.innsendingsId} med ettersendingsid=$ettersendingsId. " +
				"Med vedleggsstatus ${dokumentSoknadDto.vedleggsListe.map { it.vedleggsnr+':'+it.opplastingsStatus+':'+ mapTilLocalDateTime(it.innsendtdato) }}")

			innsenderMetrics.operationsCounterInc(operation, nyesteSoknad.tema)
			return dokumentSoknadDto
		} catch (e: Exception) {
			reportException(e, operation, nyesteSoknad.tema)
			throw e
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
				.forEach { lagreFil(savedDokumentSoknadDto, it, dokumentSoknadDto.vedleggsListe) }
			publiserBrukernotifikasjon(savedDokumentSoknadDto)

			innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
			return innsendingsId
		} catch (e: Exception) {
			reportException(e, operation, dokumentSoknadDto.tema)
			throw e
		}
	}

	private fun lagreFil(savedDokumentSoknadDto: DokumentSoknadDto, lagretVedleggDto: VedleggDto, innsendtVedleggDtos: List<VedleggDto>) {
		val matchInnsendtVedleggDto = innsendtVedleggDtos.firstOrNull {
			it.vedleggsnr == lagretVedleggDto.vedleggsnr && it.mimetype == lagretVedleggDto.mimetype && it.document?.isNotEmpty() ?: false
				&& it.erHoveddokument == lagretVedleggDto.erHoveddokument && it.erVariant == lagretVedleggDto.erVariant
		}

		if (matchInnsendtVedleggDto != null) {
			val filDto = FilDto(lagretVedleggDto.id!!, null, lagFilNavn(matchInnsendtVedleggDto), lagretVedleggDto.mimetype!!,
				matchInnsendtVedleggDto.document?.size, matchInnsendtVedleggDto.document, OffsetDateTime.now()
			)
			lagreFil(savedDokumentSoknadDto, filDto)
			return
		}
		logger.error("Fant ikke matchende lagret vedlegg med innsendt vedlegg")
		throw BackendErrorException("Fant ikke matchende lagret vedlegg ${lagretVedleggDto.tittel} med innsendt vedlegg, er variant = ${lagretVedleggDto.erVariant}",
			"Feil ved lagring av dokument ${lagretVedleggDto.tittel}, prøv igjen", "errorCode.backendError.fileInconsistencyError")
	}

	private fun filExtention(mimetype: Mimetype?): String =
		when (mimetype) {
			Mimetype.imageSlashPng -> ".png"
			Mimetype.imageSlashJpeg -> ".jpeg"
			Mimetype.applicationSlashJson -> ".json"
			Mimetype.applicationSlashPdf -> ".pdf"
			else -> ""
		}

	private fun lagFilNavn(vedleggDto: VedleggDto): String {
		val ext = filExtention(vedleggDto.mimetype)
		return (vedleggDto.vedleggsnr + ext)
	}

	fun hentAktiveSoknader(brukerIds: List<String>): List<DokumentSoknadDto>  {
		return brukerIds.flatMap { hentSoknadGittBrukerId(it) }
	}

	fun hentInnsendteSoknader(brukerIds: List<String>): List<DokumentSoknadDto>  {
		return brukerIds.flatMap { hentSoknadGittBrukerId(it, SoknadsStatus.Innsendt) }
	}

	private fun hentSoknadGittBrukerId(brukerId: String, soknadsStatus: SoknadsStatus = SoknadsStatus.Opprettet): List<DokumentSoknadDto> {
		val soknader = repo.finnAlleSoknaderGittBrukerIdOgStatus(brukerId, soknadsStatus)

		return soknader.map { hentAlleVedlegg(it) }
	}

	// Hent soknad gitt id med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(id: Long): DokumentSoknadDto {
		val soknadDbDataOpt = repo.hentSoknadDb(id)
		return hentAlleVedlegg(soknadDbDataOpt, id.toString())
	}

	// Hent soknad gitt innsendingsid med alle vedlegg. Merk at eventuelt dokument til vedlegget hentes ikke
	fun hentSoknad(innsendingsId: String): DokumentSoknadDto {
		val soknadDbDataOpt = repo.hentSoknadDb(innsendingsId)
		return hentAlleVedlegg(soknadDbDataOpt, innsendingsId)
	}

	fun endreSoknad(id: Long, visningsSteg: Long) {
		repo.endreSoknadDb(id, visningsSteg)
	}

	private fun hentAlleVedlegg(soknadDbDataOpt: Optional<SoknadDbData>, ident: String): DokumentSoknadDto {
		val operation = InnsenderOperation.HENT.name

		if (soknadDbDataOpt.isPresent) {
			innsenderMetrics.operationsCounterInc(operation, soknadDbDataOpt.get().tema)
			return hentAlleVedlegg(soknadDbDataOpt.get())

		} else {
			val e = ResourceNotFoundException(
				null,
				"Ingen soknad med id = $ident funnet",
				"errorCode.resourceNotFound.applicationNotFound"
			)
			reportException(e, operation, "Ukjent")
			throw e
		}
	}

	private fun hentAlleVedlegg(soknadDbData: SoknadDbData): DokumentSoknadDto {
		val vedleggDbDataListe = try {
			repo.hentAlleVedleggGittSoknadsid(soknadDbData.id!!)
		} catch (e: Exception) {
			throw ResourceNotFoundException("Ved oppretting av søknad skal det minimum være opprettet et vedlegg for selve søknaden",
				"Fant ingen vedlegg til soknad ${soknadDbData.innsendingsid}", "errorCode.resourceNotFound.noAttachmentsFound")
		}
		val dokumentSoknadDto = lagDokumentSoknadDto(soknadDbData, vedleggDbDataListe)
		logger.debug("hentAlleVedlegg: Hentet ${dokumentSoknadDto.innsendingsId}. " +
			"Med vedleggsstatus ${dokumentSoknadDto.vedleggsListe.map { it.vedleggsnr+':'+it.opplastingsStatus+':'+it.innsendtdato }}")

		return dokumentSoknadDto
	}

	// Hent vedlegg, merk filene knyttet til vedlegget ikke lastes opp
	fun hentVedleggDto(vedleggsId: Long): VedleggDto  {
		val vedleggDbDataOpt = repo.hentVedlegg(vedleggsId)
		if (!vedleggDbDataOpt.isPresent)
			throw ResourceNotFoundException(null, "Vedlegg med id $vedleggsId ikke funnet", "errorCode.resourceNotFound.attachmentNotFound")

		return lagVedleggDto(vedleggDbDataOpt.get())
	}

	@Transactional
	fun lagreFil(soknadDto: DokumentSoknadDto, filDto: FilDto): FilDto {
		val operation = InnsenderOperation.LAST_OPP.name

		if (soknadDto.status != SoknadsStatusDto.opprettet) {
			when (soknadDto.status.name) {
				SoknadsStatusDto.innsendt.name -> throw IllegalActionException(
					"Innsendte søknader kan ikke endres. Ønsker søker å gjøre oppdateringer, så må vedkommende ettersende dette",
					"Søknad ${soknadDto.innsendingsId} er sendt inn og nye filer kan ikke lastes opp på denne. Opprett ny søknad for ettersendelse av informasjon",
					"errorCode.illegalAction.applicationSentInOrDeleted")
				SoknadsStatusDto.slettetAvBruker.name, SoknadsStatusDto.automatiskSlettet.name -> throw IllegalActionException(
					"Søknader markert som slettet kan ikke endres. Søker må eventuelt opprette ny søknad",
					"Søknaden er slettet og ingen filer kan legges til",
					"errorCode.illegalAction.applicationSentInOrDeleted")
				else -> {
					throw BackendErrorException(
						"Ukjent status ${soknadDto.status.name}",
						"Lagring av filer på søknad med status ${soknadDto.status.name} er ikke håndtert",
						"errorCode.backendError.fileSaveError")
				}
			}
		}

		if (soknadDto.vedleggsListe.none { it.id == filDto.vedleggsid })
			throw ResourceNotFoundException(null, "Vedlegg $filDto.vedleggsid til søknad ${soknadDto.innsendingsId} eksisterer ikke", "errorCode.resourceNotFound.attachmentNotFound")

		val savedFilDbData = try {
			repo.saveFilDbData(soknadDto.innsendingsId!!, mapTilFilDb(filDto))
		} catch (e: Exception) {
			reportException(e, operation, soknadDto.tema)
			throw e
		}
		repo.oppdaterVedleggStatus(soknadDto.innsendingsId!!, filDto.vedleggsid, OpplastingsStatus.LASTET_OPP, LocalDateTime.now())
		innsenderMetrics.operationsCounterInc(operation, soknadDto.tema)
		return lagFilDto(savedFilDbData, false)
	}

	fun hentFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long): FilDto {
		val operation = InnsenderOperation.LAST_NED.name

		// Sjekk om vedlegget eksisterer
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke", "errorCode.resourceNotFound.attachmentNotFound")

		val filDbDataOpt = repo.hentFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)

		if (!filDbDataOpt.isPresent)
			when (soknadDto.status.name) {
				SoknadsStatusDto.innsendt.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknad ${soknadDto.innsendingsId} er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til Ditt Nav og søk opp dine saker der")
				SoknadsStatusDto.slettetAvBruker.name, SoknadsStatusDto.automatiskSlettet.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknaden er slettet og ingen filer er tilgjengelig")
				else -> {
					throw ResourceNotFoundException(null, "Det finnes ikke fil med id=$filId for søknad ${soknadDto.innsendingsId}", "errorCode.resourceNotFound.fileNotFound")
				}
			}

		innsenderMetrics.operationsCounterInc(operation, soknadDto.tema)
		return lagFilDto(filDbDataOpt.get())
	}

	fun hentFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggsId: Long, medFil: Boolean = false): List<FilDto> {
		return hentFiler(soknadDto, innsendingsId, vedleggsId, medFil, false)
	}

	fun hentFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggsId: Long, medFil: Boolean = false, kastFeilNarNull: Boolean = false): List<FilDto> {
		// Sjekk om vedlegget eksisterer for soknad
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad $innsendingsId eksisterer ikke", "errorCode.resourceNotFound.attachmentNotFound")

		val filDbDataList = if (medFil)
			repo.hentFilerTilVedlegg(innsendingsId, vedleggsId)
		else
			repo.hentFilerTilVedleggUtenFilData(innsendingsId, vedleggsId)

		if (filDbDataList.isEmpty() && kastFeilNarNull )
			when (soknadDto.status.name) {
				SoknadsStatusDto.innsendt.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknad $innsendingsId er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til https://www.nav.no/minside og søk opp dine saker der")
				SoknadsStatusDto.slettetAvBruker.name, SoknadsStatusDto.automatiskSlettet.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknaden er slettet og ingen filer er tilgjengelig")
				else -> {
					throw ResourceNotFoundException(null, "Ingen filer funnet for oppgitt vedlegg $vedleggsId til søknad $innsendingsId", "errorCode.resourceNotFound.fileNotFound")
				}
			}

		return filDbDataList.map { lagFilDto(it, medFil) }
	}

	fun finnFilStorrelseSum(soknadDto: DokumentSoknadDto, vedleggsId: Long): Long {
		return repo.hentSumFilstorrelseTilVedlegg(soknadDto.innsendingsId!!, vedleggsId)
	}

	fun finnFilStorrelseSum(soknadDto: DokumentSoknadDto) = soknadDto.vedleggsListe
		.filter { it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt || it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
		.sumOf { repo.hentSumFilstorrelseTilVedlegg(soknadDto.innsendingsId!!, it.id!!) }

	@Transactional
	fun slettFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long): VedleggDto {
		val operation = InnsenderOperation.SLETT_FIL.name

		// Sjekk om vedlegget eksisterer
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke",
				"errorCode.resourceNotFound.attachmentNotFound")
		if (repo.hentFilDb(soknadDto.innsendingsId!!, vedleggsId, filId).isEmpty)
			throw ResourceNotFoundException(null, "Fil $filId på vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke",
				"errorCode.resourceNotFound.fileNotFound")

		repo.slettFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)
		if (repo.hentFilerTilVedlegg(soknadDto.innsendingsId!!, vedleggsId).isEmpty()) {
			val vedleggDto = soknadDto.vedleggsListe.first { it.id == vedleggsId }
			val nyOpplastingsStatus = if (vedleggDto.innsendtdato != null) OpplastingsStatus.INNSENDT else OpplastingsStatus.IKKE_VALGT
			repo.lagreVedlegg(mapTilVedleggDb(vedleggDto, soknadsId = soknadDto.id!!, vedleggDto.skjemaurl, opplastingsStatus = nyOpplastingsStatus))
		}
		val vedleggDto = hentVedleggDto(vedleggsId)
		innsenderMetrics.operationsCounterInc(operation, soknadDto.tema)
		return vedleggDto
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

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { slettVedleggOgDensFiler(it) }
		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		repo.slettSoknad(dokumentSoknadDto)

		val soknadDbData = mapTilSoknadDb(dokumentSoknadDto, dokumentSoknadDto.innsendingsId!!, SoknadsStatus.SlettetAvBruker)
		val slettetSoknad = lagDokumentSoknadDto(soknadDbData, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!) })
		publiserBrukernotifikasjon(slettetSoknad)
		innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
	}

	private fun slettVedleggOgDensFiler(vedleggDto: VedleggDto) {
		repo.slettFilerForVedlegg(vedleggDto.id!!)
		repo.slettVedlegg(vedleggDto.id!!)
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

	@Transactional
	fun leggTilVedlegg(soknadDto: DokumentSoknadDto, tittel: String?): VedleggDto {

		val soknadDbOpt = repo.hentSoknadDb(soknadDto.innsendingsId!!)
		if (soknadDbOpt.isEmpty || soknadDbOpt.get().status != SoknadsStatus.Opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${soknadDto.innsendingsId} kan ikke endres da den er innsendt eller slettet")

		// Lagre vedlegget i databasen
		val vedleggDbDataList = opprettVedleggTilSoknad(soknadDbOpt.get().id!!, listOf("N6"), soknadDto.spraak!!, tittel)

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(vedleggDbDataList.first())
	}

	@Transactional
	fun endreVedlegg(patchVedleggDto: PatchVedleggDto, vedleggsId: Long, soknadDto: DokumentSoknadDto): VedleggDto {

		if (soknadDto.status != SoknadsStatusDto.opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${soknadDto.innsendingsId} kan ikke endres da den er innsendt eller slettet")

		val vedleggDbDataOpt = repo.hentVedlegg(vedleggsId)
		if (vedleggDbDataOpt.isEmpty)
			throw IllegalActionException(
				"Kan ikke endre vedlegg da det ikke ble funnet",
				"Fant ikke vedlegg $vedleggsId på ${soknadDto.innsendingsId}")

		val vedleggDbData = vedleggDbDataOpt.get()
		if (vedleggDbData.soknadsid != soknadDto.id) {
			throw IllegalActionException(
				"Kan ikke endre vedlegg da søknaden ikke har et slikt vedlegg",
				"Søknad ${soknadDto.innsendingsId} har ikke vedlegg med id $vedleggsId")
		}
		if (vedleggDbData.vedleggsnr != "N6" && patchVedleggDto.tittel != null) {
			throw IllegalActionException(
				"Ulovlig endring av tittel på vedlegg",
				"Vedlegg med id $vedleggsId er av type ${vedleggDbData.vedleggsnr}.Tittel kan kun endres på vedlegg av type N6 ('Annet').")
		}
/* Sletter ikke eventuelle filer som søker har lastet opp på vedlegget før vedkommende endrer status til sendSenere eller sendesAvAndre.
 	 Disse blir eventuelt slettet i forbindelse med innsending av søknader.
		slettFilerDersomStatusUlikLastetOpp(patchVedleggDto, soknadDto, vedleggsId)
*/

		val oppdatertVedlegg = repo.oppdaterVedlegg(soknadDto.innsendingsId!!, oppdaterVedleggDb(vedleggDbData, patchVedleggDto))

		if (oppdatertVedlegg.isEmpty) {
			throw BackendErrorException(null, "Vedlegg er ikke blitt oppdatert", "errorCode.backendError.attachmentUpdateError")
		}

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(oppdatertVedlegg.get(), null)
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

	@Transactional
	fun slettVedlegg(soknadDto: DokumentSoknadDto, vedleggsId: Long) {
		if (soknadDto.status != SoknadsStatusDto.opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${soknadDto.innsendingsId} kan ikke endres da den allerede er innsendt")

		val vedleggDto = soknadDto.vedleggsListe.firstOrNull { it.id == vedleggsId }
			?: throw ResourceNotFoundException(null, "Angitt vedlegg $vedleggsId eksisterer ikke for søknad ${soknadDto.innsendingsId}", "errorCode.resourceNotFound.attachmentNotFound")

		if (vedleggDto.erHoveddokument)
			throw IllegalActionException("Søknaden må alltid ha sitt hovedskjema", "Kan ikke slette hovedskjema på en søknad")
		if (!vedleggDto.vedleggsnr.equals("N6") || (vedleggDto.vedleggsnr.equals("N6") && vedleggDto.erPakrevd))
			throw IllegalActionException("Vedlegg som er obligatorisk for søknaden kan ikke slettes av søker", "Kan ikke slette påkrevd vedlegg")

		slettVedleggOgDensFiler(vedleggDto, soknadDto.id!!)
	}

	private fun slettVedleggOgDensFiler(vedleggDto: VedleggDto, soknadsId: Long) {
		// Ikke slette hovedskjema, og ikke obligatoriske. Slette vedlegget og dens opplastede filer
			slettVedleggOgDensFiler(vedleggDto)
			// Oppdatere soknad.sisteendret
		repo.oppdaterEndretDato(soknadsId)
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

		validerAtMinstEnFilErLastetOpp(soknadDto)

		// Vedleggsliste med opplastede dokument og status= LASTET_OPP for de som skal sendes soknadsfillager
		val alleVedlegg: List<VedleggDto> = ferdigstillVedlegg(soknadDto)
		val opplastedeVedlegg = alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
		val manglendePakrevdeVedlegg = alleVedlegg.filter { !it.erHoveddokument && ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6") && (it.opplastingsStatus == OpplastingsStatusDto.sendSenere || it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt) }

		logger.info("${soknadDtoInput.innsendingsId}: Opplastede vedlegg = ${opplastedeVedlegg.map { it.vedleggsnr+':'+it.uuid+':'+it.opprettetdato+':'+it.document?.size }}")
		logger.info("${soknadDtoInput.innsendingsId}: Ikke opplastede påkrevde vedlegg = ${manglendePakrevdeVedlegg.map { it.vedleggsnr+':'+it.opprettetdato }}")
		val kvitteringForArkivering = lagInnsendingsKvittering(soknadDto, opplastedeVedlegg, manglendePakrevdeVedlegg)
		try {
			fillagerAPI.lagreFiler(soknadDto.innsendingsId!!, opplastedeVedlegg + kvitteringForArkivering)
		} catch (e: Exception) {
			reportException(e, operation, soknadDto.tema)
			logger.error("Feil ved sending av filer for søknad ${soknadDto.innsendingsId} til NAV, ${e.message}")
			throw BackendErrorException(e.message, "Feil ved sending av filer for søknad ${soknadDto.innsendingsId} til NAV", "errorCode.backendError.sendToNAVError")
		}

		// send soknadmetada til soknadsmottaker
		try {
			soknadsmottakerAPI.sendInnSoknad(soknadDto, opplastedeVedlegg + kvitteringForArkivering)
		} catch (e: Exception) {
			reportException(e, operation, soknadDto.tema)
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
			reportException(e, operation, soknadDto.tema)
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

			sjekkOgOpprettEttersendingsSoknad(innsendtSoknadDto, manglende, soknadDtoInput)

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

	private fun sjekkOgOpprettEttersendingsSoknad(
		innsendtSoknadDto: DokumentSoknadDto,
		manglende: List<VedleggDto>,
		soknadDtoInput: DokumentSoknadDto
	) {
		logger.info("${innsendtSoknadDto.innsendingsId}: antall vedlegg som skal ettersendes " +
			"${innsendtSoknadDto.vedleggsListe.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.sendSenere }.size}"
		)
		if (manglende.isNotEmpty() && !"DAG".equals(innsendtSoknadDto.tema, true)) {
			logger.info("${soknadDtoInput.innsendingsId}: Skal opprette ettersendingssoknad")
			opprettEttersendingsSoknad(
				innsendtSoknadDto,
				innsendtSoknadDto.ettersendingsId ?: innsendtSoknadDto.innsendingsId!!
			)
		}
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
			reportException(e, operation, soknadDto.tema)
			throw BackendErrorException(e.message, "Feil ved generering av forside for ettersendingssøknad ${soknadDto.innsendingsId}", "errorCode.backendError.sendToNAVError")
		}
		val hovedDokumentDto = soknadDto.vedleggsListe.firstOrNull { it.erHoveddokument && !it.erVariant }
			?: lagVedleggDto(opprettHovedddokumentVedlegg(
					mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!,
					mapTilSoknadsStatus(soknadDto.status, null)),
					KodeverkSkjema(tittel = soknadDto.tittel, skjemanummer = soknadDto.skjemanr, beskrivelse = soknadDto.tittel, tema = soknadDto.tema ) ), null)

		val oppdatertSoknadDto = hentSoknad(soknadDto.id!!)
		lagreFil(oppdatertSoknadDto, FilDto(hovedDokumentDto.id!!, null, hovedDokumentDto.vedleggsnr!!, Mimetype.applicationSlashPdf,
			dummySkjema.size, dummySkjema, OffsetDateTime.now() ))

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

	fun slettfilerTilInnsendteSoknader(dagerGamle: Int) {
		logger.info("Slett alle opplastede filer for innsendte søknader mellom ${100 + dagerGamle} til $dagerGamle dager siden")
		repo.deleteAllBySoknadStatusAndInnsendtdato(dagerGamle)
	}


	private fun vedleggHarFiler(innsendingsId: String, vedleggsId: Long): Boolean {
		return repo.findAllByVedleggsid(innsendingsId, vedleggsId).any { it.data != null }
	}

	// For alle vedlegg til søknaden:
	// Hoveddokument kan ha ulike varianter. Hver enkelt av disse sendes som ulike filer til soknadsfillager.
	// Bruker kan ha lastet opp flere filer for øvrige vedlegg. Disse må merges og sendes som en fil.
	private fun ferdigstillVedlegg(soknadDto: DokumentSoknadDto): List<VedleggDto> {
		return soknadDto.vedleggsListe
			.map { lagVedleggDtoMedOpplastetFil(hentOgMergeVedleggsFiler(soknadDto, soknadDto.innsendingsId!!, it), it) }
			.sortedByDescending { it.erHoveddokument } // Hoveddokument first
			.onEach { if (!it.erHoveddokument)
				logger.info("${soknadDto.innsendingsId}: Vedlegg ${it.vedleggsnr} har opplastet fil=${it.document != null} og erPakrevd=${it.erPakrevd}")
			}
	}

	private fun validerAtMinstEnFilErLastetOpp(soknadDto: DokumentSoknadDto) {
		if (!erEttersending(soknadDto)) {
			// For å sende inn en søknad må det være lastet opp en fil på hoveddokumentet
			val harFil = soknadDto.vedleggsListe
				.filter { it.erHoveddokument && !it.erVariant && it.id != null }
				.map { it.id }
				.any { vedleggHarFiler(soknadDto.innsendingsId!!, it!!) }

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
				.any { vedleggHarFiler(soknadDto.innsendingsId!!, it!!) }
			val allePakrevdeBehandlet = soknadDto.vedleggsListe
				.filter { !it.erHoveddokument && ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6") }
				.none { !(it.opplastingsStatus == OpplastingsStatusDto.innsendt || it.opplastingsStatus == OpplastingsStatusDto.sendesAvAndre || it.opplastingsStatus == OpplastingsStatusDto.lastetOpp) }

			if (!harFil) {
				// Hvis status for alle vedlegg som foventes sendt inn er lastetOpp, Innsendt eller SendesAvAndre, ikke kast feil. Merk at kun dummy forside vil bli sendt til arkivet.
				if (allePakrevdeBehandlet) {
					val separator = "\n"
					logger.warn("Søker har ikke lastet opp filer på ettersendingssøknad ${soknadDto.innsendingsId}, " +
						"men det er ikke gjenstående arbeid på noen av de påkrevde vedleggene. Vedleggsstatus:\n" +
						soknadDto.vedleggsListe.joinToString(separator) { it.tittel + ", med status = " + it.opplastingsStatus + "\n" })
				} else {
					throw IllegalActionException(
						"Søker må ha ved ettersending til en søknad, ha lastet opp ett eller flere vedlegg for å kunnne sende inn søknaden",
						"Innsending avbrutt da ingen vedlegg er lastet opp",
						"errorCode.illegalAction.sendInErrorNoChange")
				}
			}
		}
	}

	private fun hentOgMergeVedleggsFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggDto: VedleggDto): FilDto? {
		val filer = hentFiler(soknadDto, innsendingsId, vedleggDto.id!!, medFil = true, kastFeilNarNull = false).filter { it.data != null }
		if (filer.isEmpty()) return null

		val vedleggsFil: ByteArray? =
			if (vedleggDto.erHoveddokument && vedleggDto.erVariant) {
				if (filer.size > 1) {
					logger.warn("${soknadDto.innsendingsId}: soknadDtoVedlegg ${vedleggDto.id} er hoveddokument og er variant - " +
						"${vedleggDto.tittel} har flere opplastede filer, velger første")
				}
				filer[0].data
			} else {
				PdfMerger().mergePdfer(filer.map { it.data!! })
			}

		return FilDto(vedleggDto.id!!, null, vedleggDto.vedleggsnr!!, filer[0].mimetype, vedleggsFil?.size, vedleggsFil, filer[0].opprettetdato)
	}

	private fun reportException(e: Exception, operation: String, tema: String) {
		logger.error("Feil ved operasjon $operation", e)
		innsenderMetrics.operationsErrorCounterInc(operation, tema)
	}

	fun sjekkHarAlleredeSoknadUnderArbeid(brukerId: String, skjemanr: String, ettersending: Boolean ) {

		val aktiveSoknaderGittSkjemanr = hentAktiveSoknader(listOf( brukerId)).filter { it.skjemanr == skjemanr && erEttersending(it) == ettersending }

		if (aktiveSoknaderGittSkjemanr.isNotEmpty()) {
			logger.warn("Dupliserer søknad på skjemanr=$skjemanr, søker har allerede ${aktiveSoknaderGittSkjemanr.size} under arbeid")
		}

	}

}
