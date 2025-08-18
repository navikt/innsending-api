package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.AvsenderDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.FilDto
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.NologinSoknadDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SkjemaDtoV2
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.fillager.FillagerNamespace
import no.nav.soknad.innsending.service.fillager.FillagerService
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants.TRANSACTION_TIMEOUT
import no.nav.soknad.innsending.util.mapping.SkjemaDokumentSoknadTransformer
import no.nav.soknad.innsending.util.mapping.lagDokumentSoknadDto
import no.nav.soknad.innsending.util.mapping.mapTilMimetype
import no.nav.soknad.innsending.util.mapping.mapTilSoknadDb
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class NologinSoknadService(
	private val innsendingService: InnsendingService,
	private val repo: RepositoryUtils,
	private val vedleggService: VedleggService,
	private val filService: FilService,
	private val fillagerService: FillagerService,
	private val innsenderMetrics: InnsenderMetrics,
	private val exceptionHelper: ExceptionHelper,
) {

	@Transactional(timeout=TRANSACTION_TIMEOUT)
	fun lagreOgSendInnUinnloggetSoknad(uinnloggetSoknadDto: NologinSoknadDto, applikasjon: String): KvitteringsDto {
		val operation = InnsenderOperation.OPPRETT.name

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			input = uinnloggetSoknadDto.soknadDto,
			existingSoknad = null,
			brukerId = uinnloggetSoknadDto.soknadDto.brukerDto.id,
			applikasjon =  applikasjon,
			visningsType = VisningsType.nologin
		)
		val innsendingsId = dokumentSoknadDto.innsendingsId!!
		try {
			val savedSoknadDbData = repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId))
			val soknadsid = savedSoknadDbData.id
			val savedVedleggDbData = vedleggService.saveVedleggFromDto(soknadsid!!, dokumentSoknadDto.vedleggsListe)

			val savedDokumentSoknadDto = lagDokumentSoknadDto(savedSoknadDbData, savedVedleggDbData)

			// lagre hovedkdokument (PDF) og hoveddokumentvariant (JSON)
			savedDokumentSoknadDto.vedleggsListe
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
			return innsendingService.sendInnNoLoginSoknad(savedDokumentSoknadDto, uinnloggetSoknadDto.soknadDto.avsenderId?: AvsenderDto(uinnloggetSoknadDto.soknadDto.brukerDto.id,
				AvsenderDto.IdType.FNR), uinnloggetSoknadDto.soknadDto.brukerDto)
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
		noLoginSoknadDto: NologinSoknadDto
	) {
		if (oppdatertDokumentSoknadDto.vedleggsListe.filter { !it.erHoveddokument } == null) return

		val vedleggsListe = noLoginSoknadDto.nologinVedleggList
			.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOpp }
			.map { it.copy(opplastingsStatus = OpplastingsStatusDto.LastetOpp) }
		noLoginSoknadDto.nologinVedleggList
			//.filter { it.opplastingsStatus == OpplastingsStatusDto.LastetOpp }
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

fun brukerAvsenderValidering(nologinSoknadDto: SkjemaDtoV2): String {
		return nologinSoknadDto.brukerDto.id
	}

	fun verifiserInput(uinnloggetSoknadDto: SkjemaDtoV2) {
		// InnsendingsId skal være satt av FyllUt
		if (uinnloggetSoknadDto.innsendingsId == null) {
			throw IllegalActionException(
				message = "InnsendingId er ikke satt",
				errorCode = ErrorCode.PROPERTY_NOT_SET,
			)
		}
	}

}
