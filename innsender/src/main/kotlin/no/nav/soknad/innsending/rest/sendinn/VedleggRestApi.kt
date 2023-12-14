package no.nav.soknad.innsending.rest.sendinn

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FrontendApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.VedleggService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import no.nav.soknad.pdfutilities.Validerer
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(maxAge = 3600)
@ProtectedWithClaims(issuer = Constants.TOKENX, claimMap = [Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH])
class VedleggRestApi(
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
	private val restConfig: RestConfig,
	private val filService: FilService,
	private val vedleggService: VedleggService,
) : FrontendApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")
	private val combinedLogger = CombinedLogger(logger, secureLogger)


	@Timed(InnsenderOperation.HENT)
	override fun hentVedleggsListe(innsendingsId: String): ResponseEntity<List<VedleggDto>> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å vedleggene til søknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		val vedleggsListeDto = soknadDto.vedleggsListe

		combinedLogger.log("$innsendingsId: Hentet vedleggene til søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(vedleggsListeDto)
	}

	@Timed(InnsenderOperation.HENT)
	override fun hentVedlegg(innsendingsId: String, vedleggsId: Long): ResponseEntity<VedleggDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å hente vedlegg $vedleggsId til søknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		val vedleggDto = soknadDto.vedleggsListe.firstOrNull { it.id == vedleggsId }
			?: throw ResourceNotFoundException("Ikke funnet vedlegg $vedleggsId for søknad $innsendingsId")

		combinedLogger.log("$innsendingsId: Hentet vedlegg $vedleggsId til søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(vedleggDto)
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun endreVedlegg(
		innsendingsId: String,
		vedleggsId: Long,
		patchVedleggDto: PatchVedleggDto
	): ResponseEntity<VedleggDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log(
			"$innsendingsId: Kall for å endre vedlegg $vedleggsId til søknad. " +
				"Status=${patchVedleggDto.opplastingsStatus} og tittel = ${patchVedleggDto.tittel}",
			brukerId
		)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		if ((patchVedleggDto.opplastingsStatus == OpplastingsStatusDto.ikkeValgt || patchVedleggDto.opplastingsStatus == OpplastingsStatusDto.lastetOpp)
			&& soknadDto.vedleggsListe.first { it.id == vedleggsId }.opplastingsStatus != patchVedleggDto.opplastingsStatus
		) {

			val opplastetPaVedlegg: Long = filService.finnFilStorrelseSum(soknadDto, vedleggsId)
			val opplastetPaSoknad: Long = filService.finnFilStorrelseSum(soknadDto)

			Validerer().validerStorrelse(
				innsendingsId,
				opplastetPaSoknad,
				opplastetPaVedlegg,
				restConfig.maxFileSizeSum.toLong(),
				ErrorCode.FILE_SIZE_SUM_TOO_LARGE
			)
		}
		if (!patchVedleggDto.tittel.isNullOrEmpty()) {
			Validerer().validerStorrelse(
				innsendingsId,
				0L,
				patchVedleggDto.tittel!!.length.toLong(),
				255L,
				ErrorCode.TITLE_STRING_TOO_LONG
			)
		}
		val vedleggDto = vedleggService.endreVedlegg(patchVedleggDto, vedleggsId, soknadDto)
		combinedLogger.log("$innsendingsId: Lagret vedlegg ${vedleggDto.id} til søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(vedleggDto)
	}


	@Timed(InnsenderOperation.LAST_OPP)
	override fun lagreVedlegg(innsendingsId: String, postVedleggDto: PostVedleggDto?): ResponseEntity<VedleggDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å lagre vedlegg til søknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		val vedleggDto = vedleggService.leggTilVedlegg(soknadDto, postVedleggDto)

		combinedLogger.log("$innsendingsId: Lagret vedlegg ${vedleggDto.id} til søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(vedleggDto)
	}

	@Timed(InnsenderOperation.SLETT_FIL)
	@CrossOrigin
	override fun slettVedlegg(innsendingsId: String, vedleggsId: Long): ResponseEntity<BodyStatusResponseDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å slette vedlegg $vedleggsId for søknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		vedleggService.slettVedlegg(soknadDto, vedleggsId)

		combinedLogger.log("$innsendingsId: Slettet vedlegg $vedleggsId for søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet vedlegg med id $vedleggsId"))
	}


	private fun hentOgValiderSoknad(innsendingsId: String): DokumentSoknadDto {
		val soknadDto = soknadService.hentSoknad(innsendingsId)
		tilgangskontroll.harTilgang(soknadDto)
		if (!soknadDto.kanGjoreEndringer) {
			throw IllegalActionException(
				message = "Søknaden kan ikke vises. Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
				errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
			)
		}
		return soknadDto
	}
}
