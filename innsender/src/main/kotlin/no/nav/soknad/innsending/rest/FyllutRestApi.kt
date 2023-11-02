package no.nav.soknad.innsending.rest

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FyllUtApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.PrefillService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants.CLAIM_ACR_IDPORTEN_LOA_HIGH
import no.nav.soknad.innsending.util.Constants.CLAIM_ACR_LEVEL_4
import no.nav.soknad.innsending.util.Constants.TOKENX
import no.nav.soknad.innsending.util.mapping.mapTilSkjemaDto
import no.nav.soknad.innsending.util.models.kanGjoreEndringer
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@RestController
@ProtectedWithClaims(issuer = TOKENX, claimMap = [CLAIM_ACR_LEVEL_4, CLAIM_ACR_IDPORTEN_LOA_HIGH], combineWithOr = true)
class FyllutRestApi(
	val restConfig: RestConfig,
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
	val prefillService: PrefillService
) : FyllUtApi {

	private val logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")

	// Logg både vanlig og sikker logg (med brukerId)
	fun loggBegge(melding: String, brukerId: String) {
		logger.info(melding)
		secureLogger.info("[$brukerId] $melding")
	}

	// FIXME: Fjern dette endepunktet etter at det er byttet ut
	@Timed(InnsenderOperation.OPPRETT)
	override fun fyllUt(skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		loggBegge(
			"Skal opprette søknad fra FyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}",
			brukerId
		)
		soknadService.loggWarningVedEksisterendeSoknad(brukerId, skjemaDto.skjemanr, SoknadType.soknad)

		val opprettetSoknad = soknadService.opprettNySoknad(
			SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
				skjemaDto,
				brukerId
			)
		)

		loggBegge(
			"${opprettetSoknad.innsendingsId}: Soknad fra FyllUt opprettet. Antall vedlegg fra FyllUt=${skjemaDto.vedleggsListe?.size}",
			brukerId
		)

		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(restConfig.sendInnUrl + "/" + opprettetSoknad.innsendingsId)).build()
	}

	@Timed(InnsenderOperation.OPPRETT)
	override fun fyllUtOpprettSoknad(skjemaDto: SkjemaDto, opprettNySoknad: Boolean?): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		loggBegge(
			"Skal opprette søknad fra FyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}",
			brukerId
		)

		val redirectVedPaabegyntSoknad =
			redirectVedPaabegyntSoknad(brukerId, skjemaDto.skjemanr, opprettNySoknad ?: false)
		if (redirectVedPaabegyntSoknad != null) return redirectVedPaabegyntSoknad

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(skjemaDto, brukerId)
		val opprettetSoknad = soknadService.opprettNySoknad(dokumentSoknadDto)

		loggBegge(
			"${opprettetSoknad.innsendingsId}: Soknad fra FyllUt opprettet",
			brukerId
		)

		return ResponseEntity.status(HttpStatus.CREATED).body(opprettetSoknad)
	}

	// Redirect til påbegynt søknad hvis bruker har en søknad under arbeid
	// Defaulter til å redirecte med mindre det er sendt inn eksplisitt paremeter for å opprette ny søknad likevel
	private fun redirectVedPaabegyntSoknad(
		brukerId: String,
		skjemanr: String,
		opprettNySoknad: Boolean = false
	): ResponseEntity<SkjemaDto>? {
		val aktiveSoknader = soknadService.hentAktiveSoknader(brukerId, skjemanr, SoknadType.soknad)
		val harSoknadUnderArbeid = aktiveSoknader.isNotEmpty()

		if (harSoknadUnderArbeid && !opprettNySoknad) {

			// Redirecter til den nyeste av potensielt flere aktive søknader
			val nyesteSoknad = aktiveSoknader.maxByOrNull { it.opprettetDato }

			val redirectUrl = UriComponentsBuilder
				.fromHttpUrl(restConfig.fyllUtUrl)
				.path("/$skjemanr/paabegynt")
				.queryParam("innsendingsId", nyesteSoknad?.innsendingsId)
				.build()
				.toUri()

			logger.info("Bruker har allerede søknad under arbeid for skjemanr=$skjemanr. Redirecter til denne: $redirectUrl")

			return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build()
		}
		return null
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun fyllUtOppdaterSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		loggBegge("$innsendingsId: Skal oppdatere søknad fra FyllUt", brukerId)

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			skjemaDto,
			brukerId
		)

		val oppdatertSoknad = soknadService.oppdaterSoknad(innsendingsId, dokumentSoknadDto)

		loggBegge("$innsendingsId: Soknad fra FyllUt oppdatert", brukerId)

		return ResponseEntity.status(HttpStatus.OK).body(oppdatertSoknad)
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun fyllUtUtfyltSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		loggBegge("$innsendingsId: Skal fullføre søknad fra FyllUt", brukerId)

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			skjemaDto,
			brukerId
		)

		val oppdatertSoknad = soknadService.oppdaterUtfyltSoknad(innsendingsId, dokumentSoknadDto)

		loggBegge("$innsendingsId: Utfylt søknad fra Fyllut", brukerId)

		return ResponseEntity
			.status(HttpStatus.FOUND)
			.location(URI.create(restConfig.sendInnUrl + "/" + oppdatertSoknad.innsendingsId))
			.build()
	}

	@Timed(InnsenderOperation.HENT)
	override fun fyllUtHentSoknad(innsendingsId: String): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		loggBegge("$innsendingsId: Kall fra FyllUt for å hente søknad", brukerId)

		val dokumentSoknadDto = soknadService.hentSoknadMedHoveddokumentVariant(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		loggBegge("$innsendingsId: Hentet søknad fra FyllUt", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(mapTilSkjemaDto(dokumentSoknadDto))
	}

	@Timed(InnsenderOperation.SLETT)
	override fun fyllUtSlettSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()
		loggBegge("$innsendingsId: Kall fra FyllUt for å slette søknad", brukerId)

		val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		soknadService.slettSoknadAvBruker(dokumentSoknadDto)
		loggBegge("$innsendingsId: Slettet søknad", brukerId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id $innsendingsId"))

	}

	override fun fyllUtPrefillData(properties: List<String>): ResponseEntity<PrefilledData> {
		val userId = tilgangskontroll.hentBrukerFraToken()
		loggBegge("Kall fra FyllUt for å hente prefill-data", userId)

		val prefilledData = prefillService.getPrefillData(properties, userId)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(prefilledData)
	}

	private fun validerSoknadsTilgang(dokumentSoknadDto: DokumentSoknadDto) {
		tilgangskontroll.harTilgang(dokumentSoknadDto)
		if (!dokumentSoknadDto.kanGjoreEndringer) {
			throw IllegalActionException(
				message = "Søknaden kan ikke vises. Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
				errorCode = ErrorCode.APPLICATION_SENT_IN_OR_DELETED
			)
		}
	}


}
