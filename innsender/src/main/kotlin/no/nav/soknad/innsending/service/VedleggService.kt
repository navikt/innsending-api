package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadDbData
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.repository.VedleggDbData
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class VedleggService(
	private val repo: RepositoryUtils,
	private val skjemaService: SkjemaService,
	private val innsenderMetrics: InnsenderMetrics,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun opprettHovedddokumentVedlegg(
		savedSoknadDbData: SoknadDbData, kodeverkSkjema: KodeverkSkjema
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

	fun opprettVedleggTilSoknad(
		soknadsId: Long, vedleggsnrListe: List<String>, spraak: String, tittel: String? = null
	): List<VedleggDbData> {
		val vedleggDbDataListe = vedleggsnrListe.map { nr -> skjemaService.hentSkjema(nr, spraak) }.map { v ->
			repo.lagreVedlegg(
				VedleggDbData(
					null,
					soknadsId,
					OpplastingsStatus.IKKE_VALGT,
					false,
					ervariant = false,
					false,
					v.skjemanummer != "N6",
					v.skjemanummer,
					tittel ?: v.tittel ?: "",
					tittel ?: v.tittel ?: "",
					"",
					null,
					UUID.randomUUID().toString(),
					LocalDateTime.now(),
					LocalDateTime.now(),
					null,
					v.url
				)
			)
		}
		return vedleggDbDataListe
	}

	fun opprettInnsendteVedleggTilSoknad(
		soknadsId: Long, arkivertSoknad: AktivSakDto
	): List<VedleggDbData> {
		val vedleggDbDataListe =
			arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != arkivertSoknad.skjemanr }.map { v ->
				repo.lagreVedlegg(
					VedleggDbData(
						null,
						soknadsId,
						OpplastingsStatus.INNSENDT,
						false,
						ervariant = false,
						erpdfa = true,
						erpakrevd = true,
						vedleggsnr = v.vedleggsnr,
						tittel = v.tittel,
						label = v.tittel,
						beskrivelse = "",
						mimetype = null,
						uuid = UUID.randomUUID().toString(),
						opprettetdato = mapTilLocalDateTime(arkivertSoknad.innsendtDato) ?: LocalDateTime.now(),
						endretdato = mapTilLocalDateTime(arkivertSoknad.innsendtDato) ?: LocalDateTime.now(),
						innsendtdato = mapTilLocalDateTime(arkivertSoknad.innsendtDato) ?: LocalDateTime.now(),
						vedleggsurl = null
					)
				)
			}
		return vedleggDbDataListe
	}

	fun opprettVedleggTilSoknad(soknadDbData: SoknadDbData, vedleggsListe: List<VedleggDto>): List<VedleggDbData> {

		return vedleggsListe.filter { !it.erHoveddokument }.map { v ->
			repo.lagreVedlegg(
				VedleggDbData(
					id = null,
					soknadsid = soknadDbData.id!!,
					status = if (OpplastingsStatusDto.sendSenere == v.opplastingsStatus) OpplastingsStatus.IKKE_VALGT else mapTilDbOpplastingsStatus(
						v.opplastingsStatus
					),
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
					vedleggsurl = if (v.vedleggsnr != null) skjemaService.hentSkjema(
						v.vedleggsnr!!,
						soknadDbData.spraak ?: "nb",
						false
					).url else null
				)
			)
		}
	}

	fun opprettVedleggTilSoknad(soknadDbData: SoknadDbData, arkivertSoknad: AktivSakDto): List<VedleggDbData> {

		return arkivertSoknad.innsendtVedleggDtos.filter { it.vedleggsnr != soknadDbData.skjemanr }.map { v ->
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
					vedleggsurl = skjemaService.hentSkjema(v.vedleggsnr, soknadDbData.spraak ?: "nb", false).url
				)
			)
		}
	}

	@Transactional
	fun leggTilVedlegg(soknadDto: DokumentSoknadDto, tittel: String?): VedleggDto {

		val soknadDbOpt = repo.hentSoknadDb(soknadDto.innsendingsId!!)
		if (soknadDbOpt.isEmpty || soknadDbOpt.get().status != SoknadsStatus.Opprettet) throw IllegalActionException(
			"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
			"Søknad ${soknadDto.innsendingsId} kan ikke endres da den er innsendt eller slettet"
		)

		// Lagre vedlegget i databasen
		val vedleggDbDataList = opprettVedleggTilSoknad(soknadDbOpt.get().id!!, listOf("N6"), soknadDto.spraak!!, tittel)

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(vedleggDbDataList.first())
	}

	fun hentAlleVedlegg(soknadDbDataOpt: Optional<SoknadDbData>, ident: String): DokumentSoknadDto {
		val operation = InnsenderOperation.HENT.name

		if (soknadDbDataOpt.isPresent) {
			innsenderMetrics.operationsCounterInc(operation, soknadDbDataOpt.get().tema)
			return hentAlleVedlegg(soknadDbDataOpt.get())

		} else {
			val e = ResourceNotFoundException(
				null, "Ingen soknad med id = $ident funnet", "errorCode.resourceNotFound.applicationNotFound"
			)
			reportException(e, operation, "Ukjent")
			throw e
		}
	}

	fun hentAlleVedlegg(soknadDbData: SoknadDbData): DokumentSoknadDto {
		val vedleggDbDataListe = try {
			repo.hentAlleVedleggGittSoknadsid(soknadDbData.id!!)
		} catch (e: Exception) {
			throw ResourceNotFoundException(
				"Ved oppretting av søknad skal det minimum være opprettet et vedlegg for selve søknaden",
				"Fant ingen vedlegg til soknad ${soknadDbData.innsendingsid}",
				"errorCode.resourceNotFound.noAttachmentsFound"
			)
		}
		val dokumentSoknadDto = lagDokumentSoknadDto(soknadDbData, vedleggDbDataListe)
		logger.debug("hentAlleVedlegg: Hentet ${dokumentSoknadDto.innsendingsId}. " + "Med vedleggsstatus ${dokumentSoknadDto.vedleggsListe.map { it.vedleggsnr + ':' + it.opplastingsStatus + ':' + it.innsendtdato }}")

		return dokumentSoknadDto
	}

	// Hent vedlegg, merk filene knyttet til vedlegget ikke lastes opp
	fun hentVedleggDto(vedleggsId: Long): VedleggDto {
		val vedleggDbDataOpt = repo.hentVedlegg(vedleggsId)
		if (!vedleggDbDataOpt.isPresent) throw ResourceNotFoundException(
			null,
			"Vedlegg med id $vedleggsId ikke funnet",
			"errorCode.resourceNotFound.attachmentNotFound"
		)

		return lagVedleggDto(vedleggDbDataOpt.get())
	}

	fun slettVedleggOgDensFiler(vedleggDto: VedleggDto) {
		repo.slettFilerForVedlegg(vedleggDto.id!!)
		repo.slettVedlegg(vedleggDto.id!!)
	}

	@Transactional
	fun slettVedlegg(soknadDto: DokumentSoknadDto, vedleggsId: Long) {
		if (soknadDto.status != SoknadsStatusDto.opprettet) throw IllegalActionException(
			"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
			"Søknad ${soknadDto.innsendingsId} kan ikke endres da den allerede er innsendt"
		)

		val vedleggDto = soknadDto.vedleggsListe.firstOrNull { it.id == vedleggsId } ?: throw ResourceNotFoundException(
			null,
			"Angitt vedlegg $vedleggsId eksisterer ikke for søknad ${soknadDto.innsendingsId}",
			"errorCode.resourceNotFound.attachmentNotFound"
		)

		if (vedleggDto.erHoveddokument) throw IllegalActionException(
			"Søknaden må alltid ha sitt hovedskjema",
			"Kan ikke slette hovedskjema på en søknad"
		)
		if (!vedleggDto.vedleggsnr.equals("N6") || (vedleggDto.vedleggsnr.equals("N6") && vedleggDto.erPakrevd)) throw IllegalActionException(
			"Vedlegg som er obligatorisk for søknaden kan ikke slettes av søker",
			"Kan ikke slette påkrevd vedlegg"
		)

		slettVedleggOgDensFiler(vedleggDto, soknadDto.id!!)
	}

	private fun slettVedleggOgDensFiler(vedleggDto: VedleggDto, soknadsId: Long) {
		// Ikke slette hovedskjema, og ikke obligatoriske. Slette vedlegget og dens opplastede filer
		slettVedleggOgDensFiler(vedleggDto)
		// Oppdatere soknad.sisteendret
		repo.oppdaterEndretDato(soknadsId)
	}

	@Transactional
	fun endreVedlegg(patchVedleggDto: PatchVedleggDto, vedleggsId: Long, soknadDto: DokumentSoknadDto): VedleggDto {

		if (soknadDto.status != SoknadsStatusDto.opprettet) throw IllegalActionException(
			"Det kan ikke gjøres endring på en slettet eller innsendt søknad",
			"Søknad ${soknadDto.innsendingsId} kan ikke endres da den er innsendt eller slettet"
		)

		val vedleggDbDataOpt = repo.hentVedlegg(vedleggsId)
		if (vedleggDbDataOpt.isEmpty) throw IllegalActionException(
			"Kan ikke endre vedlegg da det ikke ble funnet", "Fant ikke vedlegg $vedleggsId på ${soknadDto.innsendingsId}"
		)

		val vedleggDbData = vedleggDbDataOpt.get()
		if (vedleggDbData.soknadsid != soknadDto.id) {
			throw IllegalActionException(
				"Kan ikke endre vedlegg da søknaden ikke har et slikt vedlegg",
				"Søknad ${soknadDto.innsendingsId} har ikke vedlegg med id $vedleggsId"
			)
		}
		if (vedleggDbData.vedleggsnr != "N6" && patchVedleggDto.tittel != null) {
			throw IllegalActionException(
				"Ulovlig endring av tittel på vedlegg",
				"Vedlegg med id $vedleggsId er av type ${vedleggDbData.vedleggsnr}.Tittel kan kun endres på vedlegg av type N6 ('Annet')."
			)
		}    /* Sletter ikke eventuelle filer som søker har lastet opp på vedlegget før vedkommende endrer status til sendSenere eller sendesAvAndre.
				Disse blir eventuelt slettet i forbindelse med innsending av søknader.
				slettFilerDersomStatusUlikLastetOpp(patchVedleggDto, soknadDto, vedleggsId)
		*/

		val oppdatertVedlegg =
			repo.oppdaterVedlegg(soknadDto.innsendingsId!!, oppdaterVedleggDb(vedleggDbData, patchVedleggDto))

		if (oppdatertVedlegg.isEmpty) {
			throw BackendErrorException(
				null,
				"Vedlegg er ikke blitt oppdatert",
				"errorCode.backendError.attachmentUpdateError"
			)
		}

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(oppdatertVedlegg.get(), null)
	}


	fun vedleggHarFiler(innsendingsId: String, vedleggsId: Long): Boolean {
		return repo.findAllByVedleggsid(innsendingsId, vedleggsId).any { it.data != null }
	}

	private fun reportException(e: Exception, operation: String, tema: String) {
		logger.error("Feil ved operasjon $operation", e)
		innsenderMetrics.operationsErrorCounterInc(operation, tema)
	}
}