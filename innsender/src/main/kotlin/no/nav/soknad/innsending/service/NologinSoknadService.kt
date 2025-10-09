package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.FilDto
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SkjemaDtoV2
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.service.fillager.FillagerInterface
import no.nav.soknad.innsending.service.fillager.FillagerNamespace
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants.TRANSACTION_TIMEOUT
import no.nav.soknad.innsending.util.mapping.SkjemaDokumentSoknadTransformer
import no.nav.soknad.innsending.util.mapping.lagDokumentSoknadDto
import no.nav.soknad.innsending.util.mapping.mapTilMimetype
import no.nav.soknad.innsending.util.mapping.mapTilSoknadDb
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class NologinSoknadService(
	private val innsendingService: InnsendingService,
	private val repo: RepositoryUtils,
	private val vedleggService: VedleggService,
	private val filService: FilService,
	private val fillagerService: FillagerInterface,
	private val innsenderMetrics: InnsenderMetrics,
	private val exceptionHelper: ExceptionHelper,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun lagreOgSendInnUinnloggetSoknad(uinnloggetSoknadDto: SkjemaDtoV2, applikasjon: String): KvitteringsDto {
		val operation = InnsenderOperation.OPPRETT.name
		val bruker = uinnloggetSoknadDto.brukerDto
		val avsender = uinnloggetSoknadDto.avsenderId ?: if (bruker != null) AvsenderDto(bruker.id, AvsenderDto.IdType.FNR) else throw IllegalActionException(
			message = "Hverken bruker eller avsender er satt",
			errorCode = ErrorCode.PROPERTY_NOT_SET
		)

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			input = uinnloggetSoknadDto,
			existingSoknad = null,
			brukerId = uinnloggetSoknadDto.brukerDto?.id,
			applikasjon =  applikasjon,
			visningsType = VisningsType.nologin
		)
		val innsendingsId = dokumentSoknadDto.innsendingsId!!
		try {
			val savedSoknadDbData = repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId))
			val soknadsid = savedSoknadDbData.id
			val savedVedleggDbData = vedleggService.saveVedleggFromDto(soknadsid!!, dokumentSoknadDto.vedleggsListe)

			val savedDokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbData)

			logger.info("$innsendingsId: Lagrer hoveddokument og hoveddokumentvariant for nologin søknad")
			savedDokumentSoknadDto.vedleggsListe
				.filter { it.erHoveddokument }
				.forEach {
					filService.lagreFil(
						soknadDto = savedDokumentSoknadDto,
						FilDto(
							vedleggsid = it.id!!, id = null, filnavn = it.vedleggsnr, mimetype = it.mimetype,
							storrelse = it.document?.size, antallsider = null,
							data = if (!it.erVariant) uinnloggetSoknadDto.hoveddokument.document else uinnloggetSoknadDto.hoveddokumentVariant.document,
							opprettetdato = it.opprettetdato
						)
					)
				}

			// flytte opplastede vedleggsfiler i fillager til fil tabellen.
			logger.info("$innsendingsId: Flytter filer fra fillager til database for nologin søknad")
			lagreFiler(savedDokumentSoknadDto, uinnloggetSoknadDto)

			innsenderMetrics.incOperationsCounter(operation, dokumentSoknadDto.tema)
			return innsendingService.sendInnNoLoginSoknad(savedDokumentSoknadDto, avsender, bruker)
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, dokumentSoknadDto.tema)
			throw e
		}
	}

	/*
	Skal flytte filene fra fillager til fil tabellen for de filene som er lastet opp og som skal være med i innsendingen av søknaden.
	Status for alle vedleggene som har filer som er lastet opp og som skal være med i innsendinge skal settes til LastetOpp.
	 */
	fun lagreFiler(
		oppdatertDokumentSoknadDto: DokumentSoknadDto,
		noLoginSoknadDto: SkjemaDtoV2
	) {
		if (noLoginSoknadDto.vedleggsListe == null || noLoginSoknadDto.vedleggsListe?.filter{it.opplastingsStatus == OpplastingsStatusDto.LastetOpp}.isNullOrEmpty()) return

		val vedleggsListe = (noLoginSoknadDto.vedleggsListe ?: emptyList())
			.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOpp }

		vedleggsListe
			.forEach {
				if (it.fyllutId == null) throw IllegalActionException(
					message = "Vedlegg med id=${it.vedleggsnr} har ikke fyllutId satt",
					errorCode = ErrorCode.NOT_FOUND
				)
				if (it.filIdListe.isNullOrEmpty()) throw IllegalActionException(
					"Vedlegg med id=${it.fyllutId} har ingen filer som skal lagres i fil tabellen",
					errorCode = ErrorCode.NOT_FOUND
				)
				kopierFilerForVedlegg(
					soknadDto = oppdatertDokumentSoknadDto,
					vedleggsRef = it.fyllutId!!,
					it.filIdListe!!)
			}
	}

	fun kopierFilerForVedlegg(soknadDto: DokumentSoknadDto, vedleggsRef: String, opplastedeFiler: List<String> ){
		val vedleggDto = soknadDto.vedleggsListe.find { it.formioId == vedleggsRef }
		if (vedleggDto == null) throw IllegalActionException("Fant ikke vedlegg med id=$vedleggsRef", errorCode = ErrorCode.NOT_FOUND)

		// for hver fil hent og opprett nytt innslag i fil tabellen
		opplastedeFiler.forEach { fileId ->
			val filSomSkalKopieres = fillagerService.hentFil(filId = fileId, soknadDto.innsendingsId!!, namespace= FillagerNamespace.NOLOGIN)
			if (filSomSkalKopieres == null || filSomSkalKopieres.innhold.isEmpty()) {
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

	fun verifiserInput(uinnloggetSoknadDto: SkjemaDtoV2) {
		// InnsendingsId skal være satt av FyllUt
		val innsendingsId = uinnloggetSoknadDto.innsendingsId ?: throw IllegalActionException(
			message = "InnsendingId er ikke satt",
			errorCode = ErrorCode.PROPERTY_NOT_SET,
		)
		if (repo.existsByInnsendingsId(innsendingsId)) {
			throw IllegalActionException(
				message = "Søknad med innsendingsId $innsendingsId finnes allerede",
				errorCode = ErrorCode.SOKNAD_ALREADY_EXISTS
			)
		}
	}

}
