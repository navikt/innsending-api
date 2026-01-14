package no.nav.soknad.innsending.rest.sendinn

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.SendinnSoknadApi
import no.nav.soknad.innsending.brukernotifikasjon.NotificationOptions
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.BodyStatusResponseDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.EnvQualifier
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.PatchSoknadDto
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.logging.CombinedLogger
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(maxAge = 3600)
@ProtectedWithClaims(
	issuer = Constants.TOKENX,
	claimMap = [Constants.CLAIM_ACR_LEVEL_4, Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH],
	combineWithOr = true
)
class SoknadRestApi(
	private val soknadService: SoknadService,
	private val tilgangskontroll: Tilgangskontroll,
	private val innsendingService: InnsendingService,
	private val notificationService: NotificationService,
) : SendinnSoknadApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val combinedLogger = CombinedLogger(logger)

	@Timed(InnsenderOperation.HENT)
	override fun hentSoknad(innsendingsId: String): ResponseEntity<DokumentSoknadDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		combinedLogger.log("$innsendingsId: Kall for å hente søknad", brukerId)
		val soknadDto = hentOgValiderSoknad(innsendingsId)
		combinedLogger.log("$innsendingsId: Hentet søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(soknadDto)
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun endreSoknad(innsendingsId: String, patchSoknadDto: PatchSoknadDto): ResponseEntity<Unit> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å endre søknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		soknadService.endreSoknad(soknadDto.id!!, patchSoknadDto.visningsSteg)

		combinedLogger.log("$innsendingsId: Oppdatert søknad", brukerId)

		return ResponseEntity(HttpStatus.NO_CONTENT)
	}

	@Timed(InnsenderOperation.SLETT)
	@CrossOrigin
	override fun slettSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å slette søknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		notificationService.close(innsendingsId)
		soknadService.slettSoknadAvBruker(soknadDto)

		combinedLogger.log("$innsendingsId: Slettet søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id $innsendingsId"))
	}

	@Timed(InnsenderOperation.SEND_INN)
	override fun sendInnSoknad(
		innsendingsId: String,
		envQualifier: EnvQualifier?,
	): ResponseEntity<KvitteringsDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		combinedLogger.log("$innsendingsId: Kall for å sende inn soknad", brukerId)

		val soknadDto = hentOgValiderSoknad(innsendingsId)
		val (kvitteringsDto, ettersending) = innsendingService.sendInnSoknad(soknadDto)
		notificationService.close(innsendingsId)

		combinedLogger.log(
			"$innsendingsId: Sendt inn soknad.\n" +
				"InnsendteVedlegg=${kvitteringsDto.innsendteVedlegg?.size}, " +
				"SkalEttersendes=${kvitteringsDto.skalEttersendes?.size}, ettersendelsesfrist=${kvitteringsDto.ettersendingsfrist}",
			brukerId
		)

		if (ettersending != null) {
			notificationService.create(
				ettersending.innsendingsId!!,
				NotificationOptions(erSystemGenerert = true, envQualifier = envQualifier)
			)
		}

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(kvitteringsDto)
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
