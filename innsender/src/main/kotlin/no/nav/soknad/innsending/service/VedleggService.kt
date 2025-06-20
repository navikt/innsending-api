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
import no.nav.soknad.innsending.util.Constants.TRANSACTION_TIMEOUT
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
				id = null,
				soknadsid = savedSoknadDbData.id!!,
				status = OpplastingsStatus.IKKE_VALGT,
				erhoveddokument = true,
				ervariant = false,
				erpdfa = true,
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
				formioid = null,
				opplastingsvalgkommentarledetekst = null,
				opplastingsvalgkommentar = null
			)
		)
	}

	fun saveVedleggWithExistingData(
		soknadsId: Long,
		vedleggList: List<InnsendtVedleggDto>,
		existingSoknad: DokumentSoknadDto
	): List<VedleggDbData> {
		return vedleggList.map {
			val existingVedlegg =
				existingSoknad.vedleggsListe.firstOrNull { existingVedlegg -> existingVedlegg.vedleggsnr == it.vedleggsnr }

			val vedleggDbDatas = repo.lagreVedlegg(
				VedleggDbData(
					id = null,
					soknadsid = soknadsId,
					status = OpplastingsStatus.IKKE_VALGT,
					erhoveddokument = false,
					ervariant = false,
					erpdfa = false,
					erpakrevd = it.vedleggsnr != "N6",
					vedleggsnr = it.vedleggsnr,
					tittel = it.tittel ?: "",
					label = it.tittel,
					beskrivelse = "",
					mimetype = null,
					uuid = UUID.randomUUID().toString(),
					opprettetdato = LocalDateTime.now(),
					endretdato = LocalDateTime.now(),
					innsendtdato = existingVedlegg?.innsendtdato?.toLocalDateTime(),
					vedleggsurl = it.url,
					formioid = null,
					opplastingsvalgkommentarledetekst = null,
					opplastingsvalgkommentar = null
				)
			)
			vedleggDbDatas
		}
	}

	fun saveVedleggWithArchivedData(
		soknadsId: Long,
		vedleggList: List<InnsendtVedleggDto>,
		archivedSoknad: AktivSakDto
	): List<VedleggDbData> {

		return vedleggList.map {
			val existingVedlegg =
				archivedSoknad.innsendtVedleggDtos.firstOrNull { existingVedlegg -> existingVedlegg.vedleggsnr == it.vedleggsnr }

			val lagretVedleggDbData = repo.lagreVedlegg(
				VedleggDbData(
					id = null,
					soknadsid = soknadsId,
					status = OpplastingsStatus.IKKE_VALGT,
					erhoveddokument = false,
					ervariant = false,
					erpdfa = false,
					erpakrevd = it.vedleggsnr != "N6",
					vedleggsnr = it.vedleggsnr,
					tittel = it.tittel ?: "",
					label = it.tittel,
					beskrivelse = "",
					mimetype = null,
					uuid = UUID.randomUUID().toString(),
					opprettetdato = LocalDateTime.now(),
					endretdato = LocalDateTime.now(),
					innsendtdato = archivedSoknad.innsendtDato.toLocalDateTime(),
					vedleggsurl = it.url,
					formioid = null,
					opplastingsvalgkommentarledetekst = null,
					opplastingsvalgkommentar = null
				)
			)

			lagretVedleggDbData
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

			val lagretVedleggDbData = repo.lagreVedlegg(
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
					formioid = formioid,
					opplastingsvalgkommentarledetekst = null,
					opplastingsvalgkommentar = null
				)
			)

			lagretVedleggDbData
		}
		return vedleggDbDataListe
	}

	fun saveVedlegg(soknadsId: Long, vedleggList: List<InnsendtVedleggDto>): List<VedleggDbData> {
		return vedleggList.map {
			val lagretVedleggDbData = repo.lagreVedlegg(
				VedleggDbData(
					id = null,
					soknadsid = soknadsId,
					status = OpplastingsStatus.IKKE_VALGT,
					erhoveddokument = false,
					ervariant = false,
					erpdfa = false,
					erpakrevd = it.vedleggsnr != "N6",
					vedleggsnr = it.vedleggsnr,
					tittel = it.tittel ?: "",
					label = it.tittel,
					beskrivelse = "",
					mimetype = null,
					uuid = UUID.randomUUID().toString(),
					opprettetdato = LocalDateTime.now(),
					endretdato = LocalDateTime.now(),
					innsendtdato = null,
					vedleggsurl = it.url,
					formioid = null,
					opplastingsvalgkommentarledetekst = null,
					opplastingsvalgkommentar = null
				)
			)
			lagretVedleggDbData
		}
	}


	fun saveVedleggFromDto(
		soknadsId: Long,
		vedleggList: List<VedleggDto>,
	): List<VedleggDbData> {
		return vedleggList.map {

			val vedleggDbDatas = repo.lagreVedlegg(
				VedleggDbData(
					id = null,
					soknadsid = soknadsId,
					status = if (it.document != null) OpplastingsStatus.LASTET_OPP else OpplastingsStatus.IKKE_VALGT,
					erhoveddokument = it.erHoveddokument,
					ervariant = it.erVariant,
					erpdfa = false,
					erpakrevd = it.vedleggsnr != "N6",
					vedleggsnr = it.vedleggsnr,
					tittel = it.tittel,
					label = it.tittel,
					beskrivelse = "",
					mimetype = it.mimetype?.value,
					uuid = UUID.randomUUID().toString(),
					opprettetdato = LocalDateTime.now(),
					endretdato = LocalDateTime.now(),
					innsendtdato = null,
					vedleggsurl = it.skjemaurl,
					formioid = it.formioId,
					opplastingsvalgkommentarledetekst = it.opplastingsValgKommentarLedetekst,
					opplastingsvalgkommentar = it.opplastingsValgKommentar
				)
			)

			vedleggDbDatas
		}
	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun leggTilVedlegg(soknadDto: DokumentSoknadDto, vedleggDto: PostVedleggDto?): VedleggDto {

		val soknadDb = repo.hentSoknadDb(soknadDto.innsendingsId!!)
		if (soknadDb.status != SoknadsStatus.Opprettet && soknadDb.status != SoknadsStatus.Utfylt) throw IllegalActionException(
			"Søknad ${soknadDto.innsendingsId} kan ikke endres da den er innsendt eller slettet. Det kan ikke gjøres endring på en slettet eller innsendt søknad"
		)

		// Lagre vedlegget i databasen
		val vedleggDbDataList = saveVedlegg(
			soknadsId = soknadDb.id!!,
			vedleggsnrListe = listOf("N6"),
			spraak = soknadDto.spraak!!,
			tittel = vedleggDto?.tittel
		)

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(vedleggDbDataList.first(), null)
	}


	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun lagreNyHoveddokumentVariant(soknadDto: DokumentSoknadDto, mimetype: Mimetype): VedleggDto {

		// Lagre vedlegget i databasen
		val vedleggDbData =
			repo.lagreVedlegg(
				VedleggDbData(
					id = null,
					soknadDto.id!!,
					status = OpplastingsStatus.IKKE_VALGT,
					erhoveddokument = true,
					ervariant = true,
					erpdfa = false,
					erpakrevd = true,
					vedleggsnr = soknadDto.skjemanr,
					tittel = soknadDto.tittel,
					label = soknadDto.tittel,
					beskrivelse = "",
					mimetype = mimetype.value,
					uuid = UUID.randomUUID().toString(),
					opprettetdato = LocalDateTime.now(),
					endretdato = LocalDateTime.now(),
					innsendtdato = null,
					vedleggsurl = null,
					formioid = null,
					opplastingsvalgkommentarledetekst = null,
					opplastingsvalgkommentar = null
				)
			)

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(vedleggDbData)
	}

	fun hentAlleVedlegg(soknadDbDataOpt: SoknadDbData, ident: String): DokumentSoknadDto {
		val operation = InnsenderOperation.HENT.name

		try {
			innsenderMetrics.incOperationsCounter(operation, soknadDbDataOpt.tema)
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
		return lagVedleggDto(vedleggDbData = vedleggDbData, document = null)
	}

	fun slettVedleggOgDensFiler(vedleggDto: VedleggDto) {
		repo.slettFilerForVedlegg(vedleggDto.id!!)
		repo.slettVedlegg(vedleggDto.id!!)
	}

	@Transactional(timeout=TRANSACTION_TIMEOUT)
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

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun endreVedleggStatus(
		soknadDto: DokumentSoknadDto,
		vedleggsId: Long,
		opplastingsStatus: OpplastingsStatusDto
	) {
		if (!soknadDto.kanGjoreEndringer) throw IllegalActionException("Søknad ${soknadDto.innsendingsId} kan ikke endres da den er innsendt eller slettet. Det kan ikke gjøres endring på en slettet eller innsendt søknad")

		val vedleggDbData = repo.hentVedlegg(vedleggsId)
		if (vedleggDbData.soknadsid != soknadDto.id) {
			throw IllegalActionException("Søknad ${soknadDto.innsendingsId} har ikke vedlegg med id $vedleggsId. Kan ikke endre vedlegg da søknaden ikke har et slikt vedlegg")
		}
		repo.oppdaterVedleggStatusOgInnsendtdato(
			innsendingsId = soknadDto.innsendingsId!!,
			vedleggsId = vedleggsId,
			opplastingsStatus = mapTilDbOpplastingsStatus(opplastingsStatus),
			endretDato = LocalDateTime.now(),
			innsendtDato = null
		)
	}


	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun endreVedlegg(
		patchVedleggDto: PatchVedleggDto,
		vedleggsId: Long,
		soknadDto: DokumentSoknadDto,
		required: Boolean? = null
	): VedleggDto {

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
			repo.oppdaterVedlegg(soknadDto.innsendingsId!!, oppdaterVedleggDb(vedleggDbData, patchVedleggDto, required))

		// Oppdater soknadens sist endret dato
		repo.oppdaterEndretDato(soknadDto.id!!)

		return lagVedleggDto(vedleggDbData = oppdatertVedlegg, document = null)
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
			logger.info("Oppdatert eksisterende vedlegg ${eksisterendeVedleggsListe.map { it.vedleggsnr }}, opplastingsStatus: ${eksisterendeVedleggsListe.map { it.opplastingsStatus }}")
		} else {
			// Lag nytt vedlegg
			repo.lagreVedlegg(mapTilVedleggDb(nyttVedlegg, soknadsId))

			logger.info("Laget nytt vedlegg ${nyttVedlegg.vedleggsnr}")
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
					if (it.opplastingsStatus == OpplastingsStatusDto.LastetOpp) {
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

	fun deleteVedleggNotRelevantAnymore(innsendingsId: String, existingVedlegg: List<VedleggDto>) {
		existingVedlegg.filter { vedlegg -> vedlegg.opplastingsStatus == OpplastingsStatusDto.LastetOppIkkeRelevantLenger }
			.forEach {
				logger.info("Sletter vedlegg id:${it.id} vedleggsnr:${it.vedleggsnr} med status LASTET_OPP_IKKE_RELEVANT_LENGER")
				slettVedleggOgDensFiler(it)
			}
	}

	private fun reportException(e: Exception, operation: String, tema: String) {
		logger.error("Feil ved operasjon $operation", e)
		innsenderMetrics.incOperationsErrorCounter(operation, tema)
	}
}
