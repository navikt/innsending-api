package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.*
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.finnSpraakFraInput
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Service
class EttersendingService(
	private val repo: RepositoryUtils,
	private val innsenderMetrics: InnsenderMetrics,
	private val skjemaService: SkjemaService,
	private val brukerNotifikasjon: BrukernotifikasjonPublisher,
	private val exceptionHelper: ExceptionHelper
) {

	private val logger = LoggerFactory.getLogger(javaClass)
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
		ettersendingsId: String
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
								skjemaService.hentSkjema(v.vedleggsnr!!, nyesteSoknad.spraak ?: "nb", false).url else null
						)
					)
				}

			val dokumentSoknadDto = lagDokumentSoknadDto(savedEttersendingsSoknad, vedleggDbDataListe)
			publiserBrukernotifikasjon(dokumentSoknadDto)

			innsenderMetrics.operationsCounterInc(operation, dokumentSoknadDto.tema)
			logger.debug("opprettEttersendingsSoknad: opprettet ${dokumentSoknadDto.innsendingsId} basert på ${nyesteSoknad.innsendingsId} med ettersendingsid=$ettersendingsId. " +
				"Med vedleggsstatus ${
					dokumentSoknadDto.vedleggsListe.map {
						it.vedleggsnr + ':' + it.opplastingsStatus + ':' + mapTilLocalDateTime(
							it.innsendtdato
						)
					}
				}"
			)

			innsenderMetrics.operationsCounterInc(operation, nyesteSoknad.tema)
			return dokumentSoknadDto
		} catch (e: Exception) {
			exceptionHelper.reportException(e, operation, nyesteSoknad.tema)
			throw e
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
		if (manglende.isNotEmpty() && !"DAG".equals(innsendtSoknadDto.tema, true)) {
			logger.info("${soknadDtoInput.innsendingsId}: Skal opprette ettersendingssoknad")
			opprettEttersendingsSoknad(
				innsendtSoknadDto,
				innsendtSoknadDto.ettersendingsId ?: innsendtSoknadDto.innsendingsId!!
			)
		}
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
}