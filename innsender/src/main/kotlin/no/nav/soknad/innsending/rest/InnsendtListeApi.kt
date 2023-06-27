package no.nav.soknad.innsending.rest

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.InnsendteApi
import no.nav.soknad.innsending.model.AktivSakDto
import no.nav.soknad.innsending.model.SoknadFile
import no.nav.soknad.innsending.security.Tilgangskontroll
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.SafService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class InnsendtListeApi(
	val safService: SafService,
	val innsendingService: InnsendingService,
	val tilgangskontroll: Tilgangskontroll,
) : InnsendteApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Timed(InnsenderOperation.HENT)
	@ProtectedWithClaims(issuer = Constants.TOKENX, claimMap = [Constants.CLAIM_ACR_LEVEL_4])
	override fun aktiveSaker(): ResponseEntity<List<AktivSakDto>> {
		logger.info("Kall for å hente innsendte søknader for en bruker")

		val innsendteSoknader = safService.hentInnsendteSoknader(tilgangskontroll.hentBrukerFraToken())
		logger.info("Hentet ${innsendteSoknader.size} innsendteSoknader. Innsendtdato=${innsendteSoknader[0].innsendtDato}")
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(innsendteSoknader)
	}

	@Timed(InnsenderOperation.HENT)
	@ProtectedWithClaims(issuer = Constants.AZURE)
	override fun hentInnsendteFiler(uuids: List<String>, xInnsendingId: String): ResponseEntity<List<SoknadFile>> {
		logger.info("$xInnsendingId: Kall for å hente filene ${uuids} til en innsendt søknad")

		val innsendteFiler = innsendingService.getFiles(xInnsendingId, uuids)
		logger.info(
			"$xInnsendingId: Status for henting av følgende innsendte filer ${
				innsendteFiler.map { it.id + ":" + it.fileStatus }.toList()
			}"
		)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(innsendteFiler)
	}

}
