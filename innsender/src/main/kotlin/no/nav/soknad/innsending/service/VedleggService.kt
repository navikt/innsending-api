package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants.KVITTERINGS_NR
import no.nav.soknad.innsending.util.mapping.*
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import no.nav.soknad.innsending.util.models.vedleggsListeUtenHoveddokument
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

	fun opprettHovedddokumentVedlegg(savedSoknadDbData: SoknadDbData, kodeverkSkjema: KodeverkSkjema): VedleggDbData {
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
				vedleggsurl = kodeverkSkjema.url,
				formioid = null
			)
		)
	}

	fun saveVedlegg(
		soknadsId: Long,
		vedleggList: List<InnsendtVedleggDto>,
	): List<VedleggDbData> {
		return vedleggList.map {
			repo.lagreVedlegg(
				VedleggDbData(
					id = null,
					soknadsid = soknadsId,
					status = OpplastingsStatus.IKKE_VALGT,
					erhoveddokument = false,
					ervariant = false,
					erpdfa = false,
					erpakrevd = it.vedleggsnr != "N6",
					vedleggsnr = it.vedleggsnr,
					tittel = it.tittel,
					label = it.tittel,
					beskrivelse = "",
					mimetype = null,
					uuid = UUID.randomUUID().toString(),
					opprettetdato = LocalDateTime.now(),
					endretdato = LocalDateTime.now(),
					innsendtdato = null,
					vedleggsurl = null,
					formioid = null
				)
			)
		}
	}

	fun saveVedlegg(
		soknadsId: Long,
		vedleggsnrListe: List<String>,
		spraak: String,
		tittel: String? = null,
		formioid: String? = null
	): List<VedleggDbData> {
		val vedleggDbDataListe = vedleggsnrListe.map { nr -> skjemaService.hentSkjema(nr, spraak) }.map { v ->
			repo.lagreVedlegg(
				VedleggDbData(
					id = null,
					soknadsid = soknadsId,
					status = OpplastingsStatus.IKKE_VALGT,
					erhoveddokument = false,
					ervariant = false,
					erpdfa = false,
					erpakrevd = v.skjemanummer != "N6",
					vedleggsnr = v.skjemanummer,
					tittel = tittel ?: v.tittel ?: "",
					label = tittel ?: v.tittel ?: "",
					beskrivelse = "",
					mimetype = null,
					uuid = UUID.randomUUID().toString(),
					opprettetdato = LocalDateTime.now(),
					endretdato = LocalDateTime.now(),
					innsendtdato = null,
					vedleggsurl = v.url,
					formioid = formioid
				)
			)
		}
		return vedleggDbDataListe
	}

	fun saveVedlegg(soknadDbData: SoknadDbData, vedleggsListe: List<VedleggDto>): List<VedleggDbData> {

		return vedleggsListe.filter { !(it.erHoveddokument || it.vedleggsnr == KVITTERINGS_NR) }.map { v ->
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
					).url else null,
					formioid = v.formioId
				)
			)
		}
	}

	fun saveVedlegg(soknadDbData: SoknadDbData, arkivertSoknad: AktivSakDto): List<VedleggDbData> {

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
					vedleggsurl = skjemaService.hentSkjema(v.vedleggsnr, soknadDbData.spraak ?: "nb", false).url,
					formioid = null
				)
			)
		}
	}

	@Transactional
	fun leggTilVedlegg(soknadDto: DokumentSoknadDto, vedleggDto: PostVedleggDto?): VedleggDto {

		val soknadDb = repo.hentSoknadDb(soknadDto.innsendingsId!!)
		if (soknadDb.status != SoknadsStatus.Opprettet && soknadDb.status != SoknadsStatus.Utfylt) throw IllegalActionException(
			"Søknad ${soknadDto.innsendingsId} kan ikke endres da den er innsendt eller slettet. Det kan ikke gjøres endring på en slettet eller innsendt søknad"
		)

		// Lagre vedlegget i databasen
		val vedleggDbDataList = saveVedlegg(
			soknadDb.id!!,
			listOf("N6"),
			soknadDto.spraak!!,
			vedleggDto?.tittel
		)

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(vedleggDbDataList.first())
	}

	fun hentAlleVedlegg(soknadDbDataOpt: SoknadDbData, ident: String): DokumentSoknadDto {
		val operation = InnsenderOperation.HENT.name

		try {
			innsenderMetrics.operationsCounterInc(operation, soknadDbDataOpt.tema)
			return hentAlleVedlegg(soknadDbDataOpt)
		} catch (e: Exception) {
			reportException(e, operation, soknadDbDataOpt.tema)
			throw e
		}
	}

	fun hentAlleVedlegg(soknadDbData: SoknadDbData): DokumentSoknadDto {
		val vedleggDbDataListe = try {
			repo.hentAlleVedleggGittSoknadsid(soknadDbData.id!!)
		} catch (e: Exception) {
			throw ResourceNotFoundException("Fant ingen vedlegg til soknad ${soknadDbData.innsendingsid}. Ved oppretting av søknad skal det minimum være opprettet et vedlegg for selve søknaden")
		}
		val dokumentSoknadDto = lagDokumentSoknadDto(soknadDbData, vedleggDbDataListe)
		logger.debug("hentAlleVedlegg: Hentet ${dokumentSoknadDto.innsendingsId}. " + "Med vedleggsstatus ${dokumentSoknadDto.vedleggsListe.map { it.vedleggsnr + ':' + it.opplastingsStatus + ':' + it.innsendtdato }}")

		return dokumentSoknadDto
	}

	// Hent vedlegg, merk filene knyttet til vedlegget ikke lastes opp
	fun hentVedleggDto(vedleggsId: Long): VedleggDto {
		val vedleggDbData = repo.hentVedlegg(vedleggsId)
		return lagVedleggDto(vedleggDbData)
	}

	fun slettVedleggOgDensFiler(vedleggDto: VedleggDto) {
		repo.slettFilerForVedlegg(vedleggDto.id!!)
		repo.slettVedlegg(vedleggDto.id!!)
	}

	@Transactional
	fun slettVedlegg(soknadDto: DokumentSoknadDto, vedleggsId: Long) {
		if (!soknadDto.kanGjoreEndringer) throw IllegalActionException("Søknad ${soknadDto.innsendingsId} kan ikke endres da den allerede er innsendt. Det kan ikke gjøres endring på en slettet eller innsendt søknad.")

		val vedleggDto = soknadDto.vedleggsListe.firstOrNull { it.id == vedleggsId }
			?: throw ResourceNotFoundException("Angitt vedlegg $vedleggsId eksisterer ikke for søknad ${soknadDto.innsendingsId}")

		if (vedleggDto.erHoveddokument) throw IllegalActionException("Kan ikke slette hovedskjema på en søknad. Søknaden må alltid ha sitt hovedskjema")
		if (!vedleggDto.vedleggsnr.equals("N6") || (vedleggDto.vedleggsnr.equals("N6") && vedleggDto.erPakrevd)) throw IllegalActionException(
			"Kan ikke slette påkrevd vedlegg. Vedlegg som er obligatorisk for søknaden kan ikke slettes av søker"
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

		if (!soknadDto.kanGjoreEndringer) throw IllegalActionException("Søknad ${soknadDto.innsendingsId} kan ikke endres da den er innsendt eller slettet. Det kan ikke gjøres endring på en slettet eller innsendt søknad")

		val vedleggDbData = repo.hentVedlegg(vedleggsId)
		if (vedleggDbData.soknadsid != soknadDto.id) {
			throw IllegalActionException("Søknad ${soknadDto.innsendingsId} har ikke vedlegg med id $vedleggsId. Kan ikke endre vedlegg da søknaden ikke har et slikt vedlegg")
		}
		if (vedleggDbData.vedleggsnr != "N6" && patchVedleggDto.tittel != null) {
			throw IllegalActionException("Ulovlig endring av tittel på vedlegg. Vedlegg med id $vedleggsId er av type ${vedleggDbData.vedleggsnr}.Tittel kan kun endres på vedlegg av type N6 ('Annet').")
		}

		/* Sletter ikke eventuelle filer som søker har lastet opp på vedlegget før vedkommende endrer status til sendSenere eller sendesAvAndre.
				Disse blir eventuelt slettet i forbindelse med innsending av søknader.
				slettFilerDersomStatusUlikLastetOpp(patchVedleggDto, soknadDto, vedleggsId)
		*/

		val oppdatertVedlegg =
			repo.oppdaterVedlegg(soknadDto.innsendingsId!!, oppdaterVedleggDb(vedleggDbData, patchVedleggDto))

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(oppdatertVedlegg, null)
	}


	// Oppdater eller legg til nytt vedlegg
	fun lagreVedleggVedOppdatering(
		eksisterendeSoknad: DokumentSoknadDto,
		nyttVedlegg: VedleggDto,
		soknadsId: Long
	) {
		val eksisterendeVedleggsListe = finnEksisterendeVedleggsListe(eksisterendeSoknad, nyttVedlegg)

		if (eksisterendeVedleggsListe.isNotEmpty()) {
			oppdaterEksisterendeVedlegg(eksisterendeVedleggsListe, nyttVedlegg, soknadsId)
		} else {
			// Lag nytt vedlegg
			repo.lagreVedlegg(mapTilVedleggDb(nyttVedlegg, soknadsId))
		}

	}

	private fun finnEksisterendeVedleggsListe(
		eksisterendeSoknad: DokumentSoknadDto,
		nyttVedlegg: VedleggDto
	): List<VedleggDto> {
		val eksisterendeVedleggsListe =
			if (nyttVedlegg.vedleggsnr == "N6") {
				// Eksisterende N6 vedlegg fra fyllUt. Ignorer N6 vedlegg som er opprettet fra SendInn (vil ikke ha formioId)
				eksisterendeSoknad.vedleggsListe.filter { eksisterendeVedlegg -> eksisterendeVedlegg.formioId != null && eksisterendeVedlegg.formioId == nyttVedlegg.formioId }
			} else {
				// Oppdater hoveddokument og andre vedlegg. Hoveddokument har samme vedleggsnr=skjemanr og erVariant skiller dem
				eksisterendeSoknad.vedleggsListe.filter { eksisterendeVedlegg ->
					eksisterendeVedlegg.formioId == nyttVedlegg.formioId && eksisterendeVedlegg.vedleggsnr == nyttVedlegg.vedleggsnr && eksisterendeVedlegg.erVariant == nyttVedlegg.erVariant
				}
			}
		return eksisterendeVedleggsListe
	}

	private fun oppdaterEksisterendeVedlegg(
		eksisterendeVedleggsListe: List<VedleggDto>,
		nyttVedlegg: VedleggDto,
		soknadsId: Long
	) {
		eksisterendeVedleggsListe.forEach {
			val vedleggDbData = mapTilVedleggDb(vedleggDto = nyttVedlegg, soknadsId = soknadsId, vedleggsId = it.id!!)
			repo.lagreVedlegg(vedleggDbData)
		}
	}

	// Set status=LASTET_OPP_IKKE_RELEVANT_LENGER and erPakrevd=false for all vedlegg that are not part of the updated søknad if they have status=LASTET_OPP
	// Delete vedlegg that are not part of the updated søknad if they have any other status
	// Concerns vedlegg from fyllUt (vedlegg with formioId). Vedlegg without formioId (ex: hoveddokument and N6 vedlegg from sendInn) will still be part of the søknad
	fun updateVedleggStatuses(
		existingVedleggListe: List<VedleggDto>,
		dokumentSoknadDto: DokumentSoknadDto
	) {
		existingVedleggListe.filter { existingVedlegg ->
			existingVedlegg.formioId != null &&
				dokumentSoknadDto.vedleggsListeUtenHoveddokument.none { newVedlegg -> existingVedlegg.formioId == newVedlegg.formioId }
		}.forEach {
			it.id?.let { vedleggId ->
				dokumentSoknadDto.innsendingsId?.let { innsendingsId ->
					if (it.opplastingsStatus == OpplastingsStatusDto.lastetOpp) {
						logger.info("Vedleggstatus er ${it.opplastingsStatus}, setter status=lastetOppIkkeRelevantLenger og erPakrevd=false på vedlegg id:${it.id}")
						repo.updateVedleggErPakrevd(vedleggId, false)
						repo.updateVedleggStatus(
							innsendingsId = innsendingsId,
							vedleggsId = vedleggId,
							opplastingsStatus = OpplastingsStatus.LASTET_OPP_IKKE_RELEVANT_LENGER
						)
					} else {
						logger.info("Vedleggstatus er ${it.opplastingsStatus}, sletter vedlegg id:${it.id}")
						slettVedleggOgDensFiler(it)
					}
				}
			}
		}
	}

	fun deleteVedleggNotRelevantAnymore(existingVedlegg: List<VedleggDto>) {
		existingVedlegg.filter { vedlegg -> vedlegg.opplastingsStatus == OpplastingsStatusDto.lastetOppIkkeRelevantLenger }
			.forEach {
				logger.info("Sletter vedlegg id:${it.id} vedleggsnr:${it.vedleggsnr} med status LASTET_OPP_IKKE_RELEVANT_LENGER")
				slettVedleggOgDensFiler(it)
			}
	}


	fun vedleggHarFiler(innsendingsId: String, vedleggsId: Long): Boolean {
		return repo.findAllByVedleggsid(innsendingsId, vedleggsId).any { it.data != null }
	}

	fun enrichVedleggListFromSanity(vedleggsnrList: List<String>, sprak: String): List<InnsendtVedleggDto> {
		return vedleggsnrList.map { vedleggsnr ->
			val kodeverkSkjema = skjemaService.hentSkjema(vedleggsnr, sprak)
			InnsendtVedleggDto(
				tittel = kodeverkSkjema.tittel ?: "",
				vedleggsnr = vedleggsnr,
			)
		}
	}

	private fun reportException(e: Exception, operation: String, tema: String) {
		logger.error("Feil ved operasjon $operation", e)
		innsenderMetrics.operationsErrorCounterInc(operation, tema)
	}
}
