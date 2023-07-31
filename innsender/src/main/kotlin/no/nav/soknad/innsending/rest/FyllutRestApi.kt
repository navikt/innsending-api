package no.nav.soknad.innsending.rest

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FyllUtApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.BodyStatusResponseDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.requestlogger.LogRequest
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
import java.net.URI

@RestController
@ProtectedWithClaims(issuer = TOKENX, claimMap = [CLAIM_ACR_LEVEL_4, CLAIM_ACR_IDPORTEN_LOA_HIGH], combineWithOr = true)
class FyllutRestApi(
	val restConfig: RestConfig,
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
) : FyllUtApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	// FIXME: Fjern dette endepunktet etter at det er byttet ut
	@Timed(InnsenderOperation.OPPRETT)
	@LogRequest("skjemanr", "tittel", "tema", "spraak")
	override fun fyllUt(skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		soknadService.loggWarningVedEksisterendeSoknad(brukerId, skjemaDto.skjemanr, false)

		val opprettetSoknad = soknadService.opprettNySoknad(
			SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
				skjemaDto,
				brukerId
			)
		)

		logger.info("${opprettetSoknad.innsendingsId}: Soknad fra fyllut persistert. Antall vedlegg fra FyllUt=${skjemaDto.vedleggsListe?.size}")
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(restConfig.frontEndFortsettEndpoint + "/" + opprettetSoknad.innsendingsId)).build()
	}

	@Timed(InnsenderOperation.OPPRETT)
	@LogRequest("skjemanr", "tittel", "tema", "spraak")
	override fun fyllUtOpprettSoknad(skjemaDto: SkjemaDto, tvingOppretting: Boolean?): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		logger.info("Kall fra FyllUt for å opprette søknad for skjema ${skjemaDto.skjemanr}")
		logger.debug("Skal opprette søknad fra fyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}")

		val redirectVedEksisterendeSoknad =
			redirectVedEksisterendeSoknad(brukerId, skjemaDto.skjemanr, tvingOppretting ?: false)
		if (redirectVedEksisterendeSoknad != null) return redirectVedEksisterendeSoknad

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(skjemaDto, brukerId)
		val opprettetSoknad = soknadService.opprettNySoknad(dokumentSoknadDto)

		logger.debug("${opprettetSoknad.innsendingsId}: Soknad fra fyllut persistert. Antall vedlegg fra FyllUt=${skjemaDto.vedleggsListe?.size}")

		return ResponseEntity.status(HttpStatus.CREATED).body(opprettetSoknad)
	}

	// Redirect til eksisterende søknad hvis bruker har en søknad under arbeid
	private fun redirectVedEksisterendeSoknad(
		brukerId: String,
		skjemanr: String,
		tvingOppretting: Boolean
	): ResponseEntity<SkjemaDto>? {
		val aktiveSoknader = soknadService.hentAktiveSoknader(brukerId, skjemanr, false)
		val harSoknadUnderArbeid = aktiveSoknader.isNotEmpty()

		if (!tvingOppretting && harSoknadUnderArbeid) {
			logger.info("Bruker har allerede søknad under arbeid for skjemanr=$skjemanr. Redirecter til denne")

			// FIXME: Er det greit at vi redirecter til den første av potensielt flere aktive søknader? Er det riktig link?
			val redirectUrl = URI.create("${restConfig.frontEndFortsettEndpoint}/${aktiveSoknader.first().innsendingsId}")
			return ResponseEntity.status(HttpStatus.FOUND).location(redirectUrl).build()
		}
		return null
	}

	@Timed(InnsenderOperation.ENDRE)
	@LogRequest("skjemanr", "tittel", "tema", "spraak")
	override fun fyllUtOppdaterSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<SkjemaDto> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			skjemaDto,
			brukerId
		)

		val oppdatertSoknad = soknadService.oppdaterSoknad(innsendingsId, dokumentSoknadDto)
		return ResponseEntity.status(HttpStatus.OK).body(oppdatertSoknad)
	}

	@Timed(InnsenderOperation.ENDRE)
	@LogRequest("skjemanr", "tittel", "tema", "spraak")
	override fun fyllUtUtfyltSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			skjemaDto,
			brukerId
		)

		val oppdatertSoknad = soknadService.oppdaterUtfyltSoknad(innsendingsId, dokumentSoknadDto)
		return ResponseEntity
			.status(HttpStatus.FOUND)
			.location(URI.create(restConfig.frontEndFortsettEndpoint + "/" + oppdatertSoknad.innsendingsId))
			.build()
	}

	@Timed(InnsenderOperation.HENT)
	@LogRequest
	override fun fyllUtHentSoknad(innsendingsId: String): ResponseEntity<SkjemaDto> {
		val dokumentSoknadDto = soknadService.hentSoknadMedHoveddokumentVariant(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(mapTilSkjemaDto(dokumentSoknadDto))
	}

	@Timed(InnsenderOperation.SLETT)
	@LogRequest
	override fun fyllUtSlettSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		soknadService.slettSoknadAvBruker(dokumentSoknadDto)

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id $innsendingsId"))

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
