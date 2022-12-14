package no.nav.soknad.innsending.rest

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.FyllUtApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.SoknadService
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
class SkjemaRestApi(
	val restConfig: RestConfig,
	val soknadService: SoknadService,
	val tilgangskontroll: Tilgangskontroll,
) : FyllUtApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Timed(InnsenderOperation.OPPRETT)
	override fun fyllUt(skjemaDto: SkjemaDto): ResponseEntity<Unit> {
		logger.info("Kall fra FyllUt for å opprette søknad for skjema ${skjemaDto.skjemanr}")
		logger.debug("Skal opprette søknad fra fyllUt: ${skjemaDto.skjemanr}, ${skjemaDto.tittel}, ${skjemaDto.tema}, ${skjemaDto.spraak}")

		val brukerId = tilgangskontroll.hentBrukerFraToken()
		soknadService.sjekkHarAlleredeSoknadUnderArbeid(brukerId, skjemaDto.skjemanr, false)
		val opprettetSoknadId = soknadService.opprettNySoknad(SkjemaDokumentSoknadTransformer().konverterTilDokumentSoknadDto(skjemaDto, brukerId))
		logger.debug("opprettetSoknadId: Soknad fra fyllut persistert. Antall vedlegg fra FyllUt=${skjemaDto.vedleggsListe?.size}")
		return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(restConfig.frontEndFortsettEndpoint+opprettetSoknadId)).build()
	}
}
