package no.nav.soknad.innsending.rest

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FyllUtApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.BodyStatusResponseDto
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.service.mapTilSkjemaDto
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants.CLAIM_ACR_LEVEL_4
import no.nav.soknad.innsending.util.Constants.TOKENX
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@ProtectedWithClaims(issuer = TOKENX, claimMap = [CLAIM_ACR_LEVEL_4])
class FyllutRestApi(
	val restConfig: RestConfig,
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
) : FyllUtApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	// FIXME: Fjern dette endepunktet etter at det er byttet ut
	@Timed(InnsenderOperation.OPPRETT)
	override fun fyllUt(skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		logger.info("Kall fra FyllUt for å opprette søknad for skjema ${skjemaDto.skjemanr}")
		logger.debug("Skal opprette søknad fra fyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}")

		val brukerId = tilgangskontroll.hentBrukerFraToken()
		soknadService.sjekkHarAlleredeSoknadUnderArbeid(brukerId, skjemaDto.skjemanr, false)
		val opprettetSoknadId = soknadService.opprettNySoknad(
			SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
				skjemaDto,
				brukerId
			)
		)
		logger.debug("$opprettetSoknadId: Soknad fra fyllut persistert. Antall vedlegg fra FyllUt=${skjemaDto.vedleggsListe?.size}")
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(restConfig.frontEndFortsettEndpoint + "/" + opprettetSoknadId)).build()
	}

	@Timed(InnsenderOperation.OPPRETT)
	override fun fyllUtOpprettSoknad(skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		logger.info("Kall fra FyllUt for å opprette søknad for skjema ${skjemaDto.skjemanr}")
		logger.debug("Skal opprette søknad fra fyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}")

		val brukerId = tilgangskontroll.hentBrukerFraToken()
		soknadService.sjekkHarAlleredeSoknadUnderArbeid(brukerId, skjemaDto.skjemanr, false)
		val opprettetSoknadId = soknadService.opprettNySoknad(
			SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
				skjemaDto,
				brukerId
			)
		)
		logger.debug("$opprettetSoknadId: Soknad fra fyllut persistert. Antall vedlegg fra FyllUt=${skjemaDto.vedleggsListe?.size}")
		return ResponseEntity.status(HttpStatus.CREATED)
			.location(URI.create(restConfig.frontEndFortsettEndpoint + "/" + opprettetSoknadId)).build()
	}

	@Timed(InnsenderOperation.ENDRE)
	override fun fyllUtOppdaterSoknad(innsendingsId: String, skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		logger.info("Kall fra FyllUt for å endre søknad for skjema ${skjemaDto.skjemanr}")
		logger.debug("Skal endre søknad fra fyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}")
		val brukerId = tilgangskontroll.hentBrukerFraToken()

		val dokumentSoknadDto = SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(
			skjemaDto,
			brukerId
		)

		soknadService.oppdaterSoknad(innsendingsId, dokumentSoknadDto)
		return ResponseEntity.status(HttpStatus.OK).body(null)
	}

	@Timed(InnsenderOperation.HENT)
	override fun fyllUtHentSoknad(innsendingsId: String): ResponseEntity<SkjemaDto> {
		logger.info("Kall fra FyllUt for å hente søknad med innsendingsId $innsendingsId")

		val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		logger.info("$innsendingsId: Hentet søknad")

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(mapTilSkjemaDto(dokumentSoknadDto))
	}

	@Timed(InnsenderOperation.SLETT)
	override fun fyllUtSlettSoknad(innsendingsId: String): ResponseEntity<BodyStatusResponseDto> {
		logger.info("Kall fra FyllUt for å slette søknad med innsendingsId $innsendingsId")

		val dokumentSoknadDto = soknadService.hentSoknad(innsendingsId)
		validerSoknadsTilgang(dokumentSoknadDto)

		soknadService.slettSoknadAvBruker(dokumentSoknadDto)
		logger.info("Slettet søknad med id $innsendingsId")

		return ResponseEntity
			.status(HttpStatus.OK)
			.body(BodyStatusResponseDto(HttpStatus.OK.name, "Slettet soknad med id $innsendingsId"))

	}

	private fun validerSoknadsTilgang(dokumentSoknadDto: DokumentSoknadDto) {
		tilgangskontroll.harTilgang(dokumentSoknadDto)
		if (dokumentSoknadDto.status != SoknadsStatusDto.opprettet) {
			throw IllegalActionException(
				"Søknaden kan ikke vises",
				"Søknaden er slettet eller innsendt og kan ikke vises eller endres.",
				"errorCode.illegalAction.applicationSentInOrDeleted"
			)
		}
	}


}
