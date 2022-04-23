package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class Tilgangskontroll(
	val subjectHandler: SubjectHandlerInterface,
	val pdlService: PdlInterface) {

	private val logger = LoggerFactory.getLogger(javaClass)

	val testbrukerid = "02097225454" // TODO slett etterhvert

	fun hentBrukerFraToken(): String {
		try {
			return subjectHandler.getUserIdFromToken()
		} catch (ex: Exception) {
			logger.warn("Midlertidig bruk av testbrukerid $testbrukerid")
			return testbrukerid
		}
	}

	fun hentPersonIdents(brukerId: String): List<String> {
		return pdlService.hentPersonIdents(brukerId).map { it.ident }
	}

	fun hentPersonIdents(): List<String> {
		val brukerId = hentBrukerFraToken()
		return pdlService.hentPersonIdents(brukerId).map { it.ident }
	}

	fun harTilgang(soknadDto: DokumentSoknadDto?) {
		val brukerId = hentBrukerFraToken()
		harTilgang(soknadDto, brukerId)
	}

	fun harTilgang(soknadDto: DokumentSoknadDto?, brukerId: String?) {
		val idents = hentPersonIdents(hentBrukerFraToken())
		if (idents.contains(soknadDto?.brukerId)) return
		throw ResourceNotFoundException(null, "Søknad finnes ikke eller er ikke tilgjengelig for innlogget bruker")
	}

}
