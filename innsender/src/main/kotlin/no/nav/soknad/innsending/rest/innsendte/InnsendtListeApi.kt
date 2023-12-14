package no.nav.soknad.innsending.rest.innsendte

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.soknad.innsending.api.InnsendteApi
import no.nav.soknad.innsending.model.SoknadFile
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.supervision.InnsenderOperation
import no.nav.soknad.innsending.supervision.timer.Timed
import no.nav.soknad.innsending.util.Constants
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class InnsendtListeApi(
	val innsendingService: InnsendingService,
) : InnsendteApi {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Timed(InnsenderOperation.HENT)
	@ProtectedWithClaims(issuer = Constants.AZURE)
	override fun hentInnsendteFiler(uuids: List<String>, xInnsendingId: String): ResponseEntity<List<SoknadFile>> {
		logger.info("$xInnsendingId: Kall for å hente filene $uuids til en innsendt søknad")

		val innsendteFiler = innsendingService.getFiles(xInnsendingId, uuids)
		logger.info(
			"$xInnsendingId: Status for henting av følgende innsendte filer ${
				innsendteFiler.map { it.id + ":" + it.fileStatus + ":size=" + it.content?.size }.toList()
			}"
		)
		return ResponseEntity
			.status(HttpStatus.OK)
			.body(innsendteFiler)
	}

}
