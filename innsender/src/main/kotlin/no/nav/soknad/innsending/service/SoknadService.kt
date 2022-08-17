package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
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
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.finnSpraakFraInput
import no.nav.soknad.pdfutilities.PdfGenerator
import no.nav.soknad.pdfutilities.PdfMerger
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
	private val innsenderMetrics: InnsenderMetrics
) {

	@Value("\${ettersendingsfrist}")
	private var ettersendingsfrist: Long = 42

	private val logger = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun opprettSoknad(brukerId: String, skjemanr: String, spraak: String, vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
			// hentSkjema informasjon gitt skjemanr
		val kodeverkSkjema = hentSkjema(skjemanr, spraak)

		try {
			// lagre soknad
			val savedSoknadDbData = repo.lagreSoknad(
				SoknadDbData(
					null, Utilities.laginnsendingsId(), kodeverkSkjema.tittel ?: "", kodeverkSkjema.skjemanummer ?: "",
					kodeverkSkjema.tema ?: "", spraak, SoknadsStatus.Opprettet, brukerId, null, LocalDateTime.now(),
					LocalDateTime.now(), null, 0, VisningsType.dokumentinnsending, true
				)
			)

			// Lagre soknadens hovedvedlegg
			val skjemaDbData = repo.lagreVedlegg(
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

			val vedleggDbDataListe = opprettVedleggTilSoknad(savedSoknadDbData.id, vedleggsnrListe, spraak)

			val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe

			val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)

			publiserBrukernotifikasjon(dokumentSoknadDto)

			return dokumentSoknadDto
		} catch (e: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.OPPRETT.name, kodeverkSkjema.tema ?: "Ukjent")
			throw e
		} finally {
			innsenderMetrics.applicationCounterInc(InnsenderOperation.OPPRETT.name, kodeverkSkjema.tema ?: "Ukjent")
		}
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
			.filter{ it.vedleggsnr != arkivertSoknad.skjemanr }
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
			throw ResourceNotFoundException(re.arsak, re.message ?: "")
		} else {
			logger.warn("Skjemanr=$nr ikke funnet i Sanity. Fortsetter behandling")
			KodeverkSkjema()
		}
	}

	@Transactional
	fun opprettSoknadForEttersendingGittSkjemanr(brukerId: String, skjemanr: String, spraak: String = "nb", vedleggsnrListe: List<String> = emptyList()): DokumentSoknadDto {
		val kodeverkSkjema = try {
			// hentSkjema informasjon gitt skjemanr
			hentSkjema(skjemanr, finnSpraakFraInput(spraak))
		} catch (ex: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.OPPRETT.name, "Ukjent")
			throw ex
		}

		try {
			val innsendingsId = Utilities.laginnsendingsId()
			// lagre soknad
			val savedSoknadDbData = repo.lagreSoknad(
				SoknadDbData(
					null,
					innsendingsId,
					kodeverkSkjema.tittel ?: "",
					kodeverkSkjema.skjemanummer ?: "",
					kodeverkSkjema.tema ?: "",
					finnSpraakFraInput(spraak),
					SoknadsStatus.Opprettet,
					brukerId,
					innsendingsId, // har ikke referanse til tidligere innsendt søknad, bruker søknadens egen innsendingsId istedenfor
					LocalDateTime.now(),
					LocalDateTime.now(),
					null,
					0,
					VisningsType.ettersending,
					kanlasteoppannet = true
				)
			)

			// Lagre vedlegget for søknadens hoveddokument
			val skjemaDbData = repo.lagreVedlegg(
				VedleggDbData(
					null,
					savedSoknadDbData.id!!,
					OpplastingsStatus.INNSENDT,
					true,
					ervariant = false,
					erpdfa = true,
					erpakrevd = true,
					kodeverkSkjema.skjemanummer ?: kodeverkSkjema.vedleggsid,
					kodeverkSkjema.tittel ?: "",
					kodeverkSkjema.tittel ?: "",
					"",
					null,
					UUID.randomUUID().toString(),
					LocalDateTime.now(),
					LocalDateTime.now(),
					null,
					kodeverkSkjema.url
				)
			)

			// For hvert vedleggsnr hent definisjonen fra Sanity og lagr vedlegg.
			val vedleggDbDataListe = opprettVedleggTilSoknad(savedSoknadDbData.id, vedleggsnrListe, spraak)

			val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe

			val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)
			publiserBrukernotifikasjon(dokumentSoknadDto)

			// antatt at frontend har ansvar for å hente skjema gitt url på vegne av søker.
			return dokumentSoknadDto
		} catch (e: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.OPPRETT.name, kodeverkSkjema.tema ?: "Ukjent")
			throw e
		} finally {
			innsenderMetrics.applicationCounterInc(InnsenderOperation.OPPRETT.name, kodeverkSkjema.tema ?: "Ukjent")
		}
	}

	fun opprettSoknadForettersendingAvVedlegg(brukerId: String, ettersendingsId: String): DokumentSoknadDto {
		// Skal opprette en soknad basert på status på vedlegg som skal ettersendes.
		// Basere opplastingsstatus på nyeste innsending på ettersendingsId, dvs. nyeste soknad der innsendingsId eller ettersendingsId lik oppgitt ettersendingsId
		// Det skal være mulig å ettersende allerede ettersendte vedlegg på nytt
		val soknadDbDataList = try {
			repo.finnNyesteSoknadGittEttersendingsId(ettersendingsId)
		} catch (ex: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.OPPRETT.name, "Ukjent")
			throw BackendErrorException(ex.message, "Feil ved henting av søknad $ettersendingsId")
		}

		if (soknadDbDataList.isEmpty()) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.OPPRETT.name, "Ukjent")
			throw ResourceNotFoundException(
				"Kan ikke opprette søknad for ettersending",
				"Soknad med id $ettersendingsId som det skal ettersendes data for ble ikke funnet"
			)
		}

		return opprettEttersendingsSoknad(hentAlleVedlegg(soknadDbDataList.first()), ettersendingsId)
	}

	fun opprettSoknadForEttersendingAvVedleggGittArkivertSoknad(brukerId: String, arkivertSoknad: AktivSakDto, sprak: String, vedleggsnrListe: List<String>): DokumentSoknadDto {
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
				VisningsType.ettersending
			)
		)
		// Lagre vedlegget for søknadens hoveddokument
		val skjemaDbData = repo.lagreVedlegg(
			VedleggDbData(
				null,
				savedSoknadDbData.id!!,
				OpplastingsStatus.INNSENDT,
				erhoveddokument = true,
				ervariant = false,
				erpdfa = true,
				erpakrevd = true,
				arkivertSoknad.skjemanr,
				arkivertSoknad.tittel,
				arkivertSoknad.tittel,
				"",
				null,
				UUID.randomUUID().toString(),
				LocalDateTime.now(),
				LocalDateTime.now(),
				null,
				null
			)
		)

		val innsendtVedleggsnrListe: List<String> = arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != arkivertSoknad.skjemanr }.map { it.vedleggsnr }.toList()
		val vedleggDbDataListe = opprettVedleggTilSoknad(savedSoknadDbData.id, vedleggsnrListe.filter { !innsendtVedleggsnrListe.contains(it) }, sprak)
		val innsendtVedleggDbDataListe = opprettInnsendteVedleggTilSoknad(savedSoknadDbData.id, arkivertSoknad)
		val savedVedleggDbDataListe = listOf(skjemaDbData) + vedleggDbDataListe + innsendtVedleggDbDataListe

		val dokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbDataListe)
		publiserBrukernotifikasjon(dokumentSoknadDto)

		return dokumentSoknadDto
	}

	private fun opprettEttersendingsSoknad (
		nyesteSoknad: DokumentSoknadDto,
		ettersendingsId: String
	): DokumentSoknadDto {
		try {

			val savedEttersendingsSoknad = repo.lagreSoknad(
				SoknadDbData(
					null,
					Utilities.laginnsendingsId(),
					nyesteSoknad.tittel,
					nyesteSoknad.skjemanr,
					nyesteSoknad.tema,
					nyesteSoknad.spraak,
					SoknadsStatus.Opprettet,
					nyesteSoknad.brukerId,
					ettersendingsId,
					LocalDateTime.now(),
					LocalDateTime.now(),
					null,
					0,
					VisningsType.ettersending
				)
			)

			val vedleggDbDataListe = nyesteSoknad.vedleggsListe
				.map { v ->
					repo.lagreVedlegg(
						VedleggDbData(
							null,
							savedEttersendingsSoknad.id!!,
							if (OpplastingsStatusDto.sendSenere == v.opplastingsStatus)
								OpplastingsStatus.IKKE_VALGT else mapTilDbOpplastingsStatus(v.opplastingsStatus),
							v.erHoveddokument,
							v.erVariant,
							v.erPdfa,
							v.erPakrevd,
							v.vedleggsnr,
							v.tittel,
							v.label,
							v.beskrivelse,
							mapTilDbMimetype(v.mimetype),
							UUID.randomUUID().toString(),
							v.opprettetdato.toLocalDateTime(),
							LocalDateTime.now(),
							v.innsendtdato?.toLocalDateTime(),
							if (v.vedleggsnr != null) hentSkjema(v.vedleggsnr!!, nyesteSoknad.spraak ?: "nb").url else null
						)
					)
				}

			val dokumentSoknadDto = lagDokumentSoknadDto(savedEttersendingsSoknad, vedleggDbDataListe)
			publiserBrukernotifikasjon(dokumentSoknadDto)

			innsenderMetrics.applicationCounterInc(InnsenderOperation.OPPRETT.name, dokumentSoknadDto.tema)
			return dokumentSoknadDto
		} catch (e: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.OPPRETT.name, nyesteSoknad.tema)
			throw e
		}
	}

	@Transactional
	fun opprettNySoknad(dokumentSoknadDto: DokumentSoknadDto): String {
		val innsendingsId = Utilities.laginnsendingsId()
		try {
			val savedSoknadDbData = repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId))
			val soknadsid = savedSoknadDbData.id
			val savedVedleggDbData = dokumentSoknadDto.vedleggsListe
				.map {
					repo.lagreVedlegg(mapTilVedleggDb(it,	soknadsid!!))
				}

			val savedDokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbData)
			// lagre mottatte filer i fil tabellen.
			savedDokumentSoknadDto.vedleggsListe
				.filter { it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
				.forEach { lagreFil(savedDokumentSoknadDto, it, dokumentSoknadDto.vedleggsListe )}
			publiserBrukernotifikasjon(savedDokumentSoknadDto)

			innsenderMetrics.applicationCounterInc(InnsenderOperation.OPPRETT.name, dokumentSoknadDto.tema)
			return innsendingsId
		} catch (e: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.OPPRETT.name, dokumentSoknadDto.tema)
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
			"Feil ved lagring av dokument ${lagretVedleggDto.tittel}, prøv igjen")

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
		val soknader = mutableListOf<DokumentSoknadDto>()
		brukerIds.stream()
			.forEach {soknader.addAll(hentSoknadGittBrukerId(it))}
		return soknader
	}

	fun hentInnsendteSoknader(brukerIds: List<String>): List<DokumentSoknadDto>  {
		val soknader = mutableListOf<DokumentSoknadDto>()
		brukerIds.stream()
			.forEach {soknader.addAll(hentSoknadGittBrukerId(it, SoknadsStatus.Innsendt))}
		return soknader
	}

	private fun hentSoknadGittBrukerId(brukerId: String, soknadsStatus: SoknadsStatus = SoknadsStatus.Opprettet): List<DokumentSoknadDto> {
		val soknader = repo.finnAlleSoknaderGittBrukerIdOgStatus(brukerId, soknadsStatus)

		return soknader.stream()
			.map { hentAlleVedlegg(it)}
			.toList()
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
		if (soknadDbDataOpt.isPresent) {
			innsenderMetrics.applicationCounterInc(InnsenderOperation.HENT.name, soknadDbDataOpt.get().tema)
			return hentAlleVedlegg(soknadDbDataOpt.get())
		}
		innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.HENT.name, "Ukjent")
		throw ResourceNotFoundException(null, "Ingen soknad med id = $ident funnet")
	}

	private fun hentAlleVedlegg(soknadDbData: SoknadDbData): DokumentSoknadDto {
		val vedleggDbDataListe = try {
			repo.hentAlleVedleggGittSoknadsid(soknadDbData.id!!)
		} catch (ex: Exception) {
			throw ResourceNotFoundException("Ved oppretting av søknad skal det minimum være opprettet et vedlegg for selve søknaden", "Fant ingen vedlegg til soknad ${soknadDbData.innsendingsid}")
		}
		return lagDokumentSoknadDto(soknadDbData, vedleggDbDataListe)
	}

	// Hent vedlegg, merk filene knyttet til vedlegget ikke lastes opp
	fun hentVedleggDto(vedleggsId: Long): VedleggDto  {
		val vedleggDbDataOpt = repo.hentVedlegg(vedleggsId)
		if (!vedleggDbDataOpt.isPresent)
			throw ResourceNotFoundException(null, "Vedlegg med id $vedleggsId ikke funnet")

		return lagVedleggDto(vedleggDbDataOpt.get())
	}

	@Transactional
	fun lagreFil(soknadDto: DokumentSoknadDto, filDto: FilDto): FilDto {
		if (soknadDto.status != SoknadsStatusDto.opprettet) {
			when (soknadDto.status.name) {
				SoknadsStatusDto.innsendt.name -> throw IllegalActionException(
					"Innsendte søknader kan ikke endres. Ønsker søker å gjøre oppdateringer, så må vedkommende ettersende dette",
					"Søknad ${soknadDto.innsendingsId} er sendt inn og nye filer kan ikke lastes opp på denne. Opprett ny søknad for ettersendelse av informasjon")
				SoknadsStatusDto.slettetAvBruker.name, SoknadsStatusDto.automatiskSlettet.name -> throw IllegalActionException(
					"Søknader markert som slettet kan ikke endres. Søker må eventuelt opprette ny søknad",
					"Søknaden er slettet og ingen filer kan legges til")
				else -> {
					throw IllegalActionException(
						"Ukjent status ${soknadDto.status.name}",
						"Lagring av filer på søknad med status ${soknadDto.status.name} er ikke håndtert")
				}
			}
		}

		if (soknadDto.vedleggsListe.none { it.id == filDto.vedleggsid })
			throw ResourceNotFoundException(null, "Vedlegg $filDto.vedleggsid til søknad ${soknadDto.innsendingsId} eksisterer ikke")

		val savedFilDbData = try {
			repo.saveFilDbData(soknadDto.innsendingsId!!, mapTilFilDb(filDto))
		} catch (ex: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.LAST_OPP.name, soknadDto.tema)
			throw ex
		}
		repo.oppdaterVedleggStatus(soknadDto.innsendingsId!!, filDto.vedleggsid, OpplastingsStatus.LASTET_OPP, LocalDateTime.now())
		innsenderMetrics.applicationCounterInc(InnsenderOperation.LAST_OPP.name, soknadDto.tema)
		return lagFilDto(savedFilDbData)
	}

	fun hentFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long): FilDto {
		// Sjekk om vedlegget eksisterer
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke")

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
					throw ResourceNotFoundException(null, "Det finnes ikke fil med id=$filId for søknad ${soknadDto.innsendingsId}")
				}
			}

		innsenderMetrics.applicationCounterInc(InnsenderOperation.LAST_NED.name, soknadDto.tema)
		return lagFilDto(filDbDataOpt.get())
	}

	fun hentFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggsId: Long, medFil: Boolean = false): List<FilDto> {
		return hentFiler(soknadDto, innsendingsId, vedleggsId, medFil, false)
	}

	fun hentFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggsId: Long, medFil: Boolean = false, kastFeilNarNull: Boolean = false): List<FilDto> {
		// Sjekk om vedlegget eksisterer for soknad
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad $innsendingsId eksisterer ikke")

		val filDbDataList = repo.hentFilerTilVedlegg(innsendingsId, vedleggsId)
		if (filDbDataList.isEmpty() && kastFeilNarNull )
			when (soknadDto.status.name) {
				SoknadsStatusDto.innsendt.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknad $innsendingsId er sendt inn og opplastede filer er ikke tilgjengelig her. Gå til Ditt Nav og søk opp dine saker der")
				SoknadsStatusDto.slettetAvBruker.name, SoknadsStatusDto.automatiskSlettet.name -> throw IllegalActionException(
					"Etter innsending eller sletting av søknad, fjernes opplastede filer fra applikasjonen",
					"Søknaden er slettet og ingen filer er tilgjengelig")
				else -> {
					throw ResourceNotFoundException(null, "Ingen filer funnet for oppgitt vedlegg $vedleggsId til søknad $innsendingsId")
				}
			}

		return filDbDataList.map { lagFilDto(it, medFil) }
	}

	@Transactional
	fun slettFil(soknadDto: DokumentSoknadDto, vedleggsId: Long, filId: Long) {
		// Sjekk om vedlegget eksisterer
		if (soknadDto.vedleggsListe.none { it.id == vedleggsId })
			throw ResourceNotFoundException(null, "Vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke")
		if (repo.hentFilDb(soknadDto.innsendingsId!!, vedleggsId, filId ).isEmpty)
			throw ResourceNotFoundException(null, "Fil $filId på vedlegg $vedleggsId til søknad ${soknadDto.innsendingsId} eksisterer ikke")

		repo.slettFilDb(soknadDto.innsendingsId!!, vedleggsId, filId)
		if (repo.hentFilerTilVedlegg(soknadDto.innsendingsId!!, vedleggsId).isEmpty()) {
			repo.oppdaterVedleggStatus(soknadDto.innsendingsId!!, vedleggsId, OpplastingsStatus.IKKE_VALGT, LocalDateTime.now())
		}
		innsenderMetrics.applicationCounterInc(InnsenderOperation.SLETT_FIL.name, soknadDto.tema)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAvBruker(dokumentSoknadDto: DokumentSoknadDto) {
		// slett vedlegg og soknad
		if (dokumentSoknadDto.status != SoknadsStatusDto.opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den er innsendt eller slettet")

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { slettVedleggOgDensFiler(it) }
		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		repo.slettSoknad(dokumentSoknadDto)

		val slettetSoknad = lagDokumentSoknadDto(mapTilSoknadDb(dokumentSoknadDto, dokumentSoknadDto.innsendingsId!!, SoknadsStatus.SlettetAvBruker)
			, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!)})
		publiserBrukernotifikasjon(slettetSoknad)
		innsenderMetrics.applicationCounterInc(InnsenderOperation.SLETT.name, dokumentSoknadDto.tema)
	}

	private fun slettVedleggOgDensFiler(vedleggDto: VedleggDto) {
		repo.slettFilerForVedlegg(vedleggDto.id!!)
		repo.slettVedlegg(vedleggDto.id!!)
	}

	// Slett opprettet soknad gitt innsendingsId
	@Transactional
	fun slettSoknadAutomatisk(innsendingsId: String) {
		// Ved automatisk sletting beholdes innslag i basen, men eventuelt opplastede filer slettes
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		if (dokumentSoknadDto.status != SoknadsStatusDto.opprettet)
			throw IllegalActionException(
				"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
				"Søknad ${dokumentSoknadDto.innsendingsId} kan ikke slettes da den allerede er innsendt")

		//fillagerAPI.slettFiler(innsendingsId, dokumentSoknadDto.vedleggsListe)
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettFilerForVedlegg(it.id!!) }
		val slettetSoknadDb = repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.AutomatiskSlettet))

		val slettetSoknadDto = lagDokumentSoknadDto(slettetSoknadDb
			, dokumentSoknadDto.vedleggsListe.map { mapTilVedleggDb(it, dokumentSoknadDto.id!!)})
		publiserBrukernotifikasjon(slettetSoknadDto)
		logger.info("slettSoknadAutomatisk: Status for søknad $innsendingsId er satt til ${SoknadsStatus.AutomatiskSlettet}")

		innsenderMetrics.applicationCounterInc(InnsenderOperation.SLETT.name, dokumentSoknadDto.tema)
	}

	@Transactional
	fun slettSoknadPermanent(innsendingsId: String) {
		val dokumentSoknadDto = hentSoknad(innsendingsId)

		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettFilerForVedlegg(it.id!!) }
		dokumentSoknadDto.vedleggsListe.filter { it.id != null }.forEach { repo.slettVedlegg(it.id!!) }
		repo.slettSoknad(dokumentSoknadDto)

		logger.info("slettSoknadPermanent: Søknad $innsendingsId er permanent slettet")

		innsenderMetrics.applicationCounterInc(InnsenderOperation.SLETT.name, dokumentSoknadDto.tema)
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
			throw BackendErrorException(null, "Vedlegg er ikke blitt oppdatert")
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
			?: throw ResourceNotFoundException(null, "Angitt vedlegg $vedleggsId eksisterer ikke for søknad ${soknadDto.innsendingsId}")

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
	fun sendInnSoknad(soknadDtoInput: DokumentSoknadDto): KvitteringsDto {

		// Det er ikke nødvendig å opprette og lagre kvittering(L7) i følge diskusjon 3/11.

		// anta at filene til et vedlegg allerede er konvertert til PDF ved lagring, men må merges og sendes til soknadsfillager
		// dersom det ikke er lastet opp filer på et obligatorisk vedlegg, skal status settes SENDES_SENERE
		// etter at vedleggsfilen er overført soknadsfillager, skal lokalt lagrede filer på vedlegget slettes.

		var soknadDto = soknadDtoInput
		if (erEttersending(soknadDto)) {
			// Hvis ettersending, så må det genereres et dummy hoveddokument
			val hovedDokumentDto = soknadDto.vedleggsListe.first { it.erHoveddokument && !it.erVariant }
			val dummySkjema = try {
				PdfGenerator().lagForsideEttersending(soknadDto)
			} catch (ex: Exception) {
				innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.SEND_INN.name, soknadDto.tema)
				throw BackendErrorException(ex.message, "Feil ved generering av forside for ettersendingssøknad ${soknadDto.innsendingsId}")
			}
			lagreFil(soknadDto, FilDto(hovedDokumentDto.id!!, null, hovedDokumentDto.vedleggsnr!!, Mimetype.applicationSlashPdf,
				dummySkjema.size, dummySkjema, OffsetDateTime.now() ))
			soknadDto = hentSoknad(soknadDto.innsendingsId!!)
		}

		validerAtMinstEnFilErLastetOpp(soknadDto)

		// Vedleggsliste med opplastede dokument og status= LASTET_OPP for de som skal sendes soknadsfillager
		val alleVedlegg: List<VedleggDto> = ferdigstillVedlegg(soknadDto)
		val opplastedeVedlegg = alleVedlegg.filter { it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }.toList()
		val manglendePakrevdeVedlegg = alleVedlegg.filter { !it.erHoveddokument && it.erPakrevd && (it.opplastingsStatus == OpplastingsStatusDto.sendSenere || it.opplastingsStatus == OpplastingsStatusDto.ikkeValgt) }.toList()

		logger.info("${soknadDtoInput.innsendingsId}: Antall opplastede vedlegg = ${opplastedeVedlegg.size}")
		logger.info("${soknadDtoInput.innsendingsId}: Antall ikke opplastede påkrevde vedlegg = ${manglendePakrevdeVedlegg.size}")
		try {
			fillagerAPI.lagreFiler(soknadDto.innsendingsId!!, opplastedeVedlegg)
		} catch (ex: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.SEND_INN.name, soknadDto.tema)
			throw BackendErrorException(ex.message, "Feil ved sending av filer for søknad ${soknadDto.innsendingsId} til NAV")
		}

		// send soknadmetada til soknadsmottaker
		try {
			soknadsmottakerAPI.sendInnSoknad(soknadDto, opplastedeVedlegg)
		} catch (ex: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.SEND_INN.name, soknadDto.tema)
			logger.error("${soknadDto.innsendingsId}: Feil ved sending av søknad til soknadsmottaker ${ex.message}")
			throw BackendErrorException(ex.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV")
		}

		// Slett alle opplastede vedlegg untatt søknaden dersom ikke ettersendingssøknad, som er sendt til soknadsfillager.
		alleVedlegg.filter{ !(it.erHoveddokument && soknadDto.visningsType != VisningsType.ettersending) }.forEach { repo.slettFilerForVedlegg(it.id!!) }

		// oppdater vedleggstabelen med status og innsendingsdato for opplastede vedlegg.
		opplastedeVedlegg.forEach { repo.oppdaterVedleggStatus(soknadDto.innsendingsId!!, it.id!!, OpplastingsStatus.INNSENDT, LocalDateTime.now()) }
		manglendePakrevdeVedlegg.forEach { repo.oppdaterVedleggStatus(soknadDto.innsendingsId!!, it.id!!, OpplastingsStatus.SEND_SENERE, LocalDateTime.now()) }

		try {
			repo.flushVedlegg()
		} catch (ex: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.SEND_INN.name, soknadDto.tema)
			throw BackendErrorException(ex.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV")
		}

		try {
			repo.soknadSaveAndFlush(mapTilSoknadDb(soknadDto, soknadDto.innsendingsId!!, SoknadsStatus.Innsendt ))
		} catch (ex: Exception) {
			innsenderMetrics.applicationErrorCounterInc(InnsenderOperation.SEND_INN.name, soknadDto.tema)
			throw BackendErrorException(ex.message, "Feil ved sending av søknad ${soknadDto.innsendingsId} til NAV")
		}
		// send brukernotifikasjon ved endring av søknadsstatus til innsendt
		val innsendtSoknadDto = hentSoknad(soknadDto.innsendingsId!!)
		publiserBrukernotifikasjon(innsendtSoknadDto)

		logger.info("${innsendtSoknadDto.innsendingsId}: antall vedlegg som skal ettersendes ${innsendtSoknadDto.vedleggsListe.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.sendSenere }.size }")
		if (manglendePakrevdeVedlegg.isNotEmpty())  { // TODO avklare lage unntak for søknader på tema DAG?
			logger.info("${soknadDtoInput.innsendingsId}: Skal opprette ettersendingssoknad")
			opprettEttersendingsSoknad(innsendtSoknadDto, innsendtSoknadDto.ettersendingsId ?: innsendtSoknadDto.innsendingsId!!)
		}

		val kvitteringsDto = lagKvittering(innsendtSoknadDto, opplastedeVedlegg, manglendePakrevdeVedlegg)
		innsenderMetrics.applicationCounterInc(InnsenderOperation.SEND_INN.name, soknadDto.tema)

		return kvitteringsDto
	}

	private fun publiserBrukernotifikasjon(dokumentSoknadDto: DokumentSoknadDto): Boolean = try {
		brukerNotifikasjon.soknadStatusChange(dokumentSoknadDto)
	} catch (ex: Exception) {
		throw BackendErrorException(
			ex.message,
			"Feil i lagring av informasjon om søknad ${dokumentSoknadDto.tittel} til Ditt NAV"
		)
	}

	private fun lagKvittering(innsendtSoknadDto: DokumentSoknadDto,
														opplastedeVedlegg: List<VedleggDto>, manglendePakrevdeVedlegg: List<VedleggDto>): KvitteringsDto {
		val hoveddokumentVedleggsId = innsendtSoknadDto.vedleggsListe.firstOrNull{ it.erHoveddokument && !it.erVariant }?.id
		val hoveddokumentFilId =
		if (hoveddokumentVedleggsId != null) {
			repo.findAllByVedleggsid(innsendtSoknadDto.innsendingsId!!, hoveddokumentVedleggsId).firstOrNull()?.id
		} else {
			null
		}
		return KvitteringsDto(innsendtSoknadDto.innsendingsId!!, innsendtSoknadDto.tittel, innsendtSoknadDto.innsendtDato!!,
			lenkeTilDokument(innsendtSoknadDto.innsendingsId!!, hoveddokumentVedleggsId, hoveddokumentFilId ),
			opplastedeVedlegg.filter{ !it.erHoveddokument }.map {InnsendtVedleggDto(it.vedleggsnr ?: "", it.label)}.toList(),
			manglendePakrevdeVedlegg.map { InnsendtVedleggDto(it.vedleggsnr ?: "", it.label) }.toList(),
			innsendtSoknadDto.innsendtDato!!.plusDays(ettersendingsfrist)
		)

	}

	private fun lenkeTilDokument(innsendingsId: String, vedleggsId: Long?, filId: Long?) = if (filId == null) null else "frontend/v1/soknad/$innsendingsId/vedlegg/$vedleggsId/fil/$filId"

	fun slettGamleSoknader(dagerGamle: Long, permanent: Boolean = false ) {
		val slettFor = LocalDateTime.now().minusDays(dagerGamle).atOffset(ZoneOffset.UTC)
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
		val vedleggDtos = mutableListOf<VedleggDto>()

		// Listen av varianter av hoveddokumenter, hent lagrede filer og opprett
		soknadDto.vedleggsListe.filter{ it.erHoveddokument }.forEach {
			vedleggDtos.add(lagVedleggDtoMedOpplastetFil(hentOgMergeVedleggsFiler(soknadDto, soknadDto.innsendingsId!!, it), it) ) }
		// For hvert øvrige vedlegg merge filer og legg til
		soknadDto.vedleggsListe.filter { !it.erHoveddokument }.forEach {
			val filDto = hentOgMergeVedleggsFiler(soknadDto, soknadDto.innsendingsId!!, it)
			logger.info("${soknadDto.innsendingsId}: Vedlegg ${it.vedleggsnr} har opplastet fil= ${filDto?.data != null} og erPakrevd=${it.erPakrevd} ")
			vedleggDtos.add(lagVedleggDtoMedOpplastetFil(filDto, it)) }
		return vedleggDtos
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
					"Innsending avbrutt da hoveddokument ikke finnes"
				)
			}
		} else {
			// For å sende inn en ettersendingssøknad må det være lastet opp minst ett vedlegg
			val harFil = soknadDto.vedleggsListe
				.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatusDto.lastetOpp }
				.map { it.id }
				.any { vedleggHarFiler(soknadDto.innsendingsId!!, it!!) }
			if (!harFil) {
				throw IllegalActionException(
					"Søker må ha ved ettersending til en søknad, ha lastet opp ett eller flere vedlegg for å kunnne sende inn søknaden",
					"Innsending avbrutt da ingen vedlegg er lastet opp"
				)
			}
		}
	}

	private fun hentOgMergeVedleggsFiler(soknadDto: DokumentSoknadDto, innsendingsId: String, vedleggDto: VedleggDto): FilDto? {
		val filer = hentFiler(soknadDto, innsendingsId, vedleggDto.id!!, medFil = true, kastFeilNarNull = false).filter { it.data != null }
		if (filer.isEmpty()) return null

		val vedleggsFil: ByteArray? =
			if (vedleggDto.erHoveddokument) {
				if (filer.size > 1) {
					logger.warn("Vedlegg ${vedleggDto.id}: ${vedleggDto.tittel} har flere opplastede filer, velger første")
				}
				filer[0].data
			} else {
				PdfMerger().mergePdfer(filer.map { it.data!! })
			}

		return FilDto(vedleggDto.id!!, null, vedleggDto.vedleggsnr!!, filer[0].mimetype, vedleggsFil?.size, vedleggsFil, filer[0].opprettetdato)
	}



}
