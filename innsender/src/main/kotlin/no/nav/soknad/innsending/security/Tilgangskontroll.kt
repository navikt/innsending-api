package no.nav.soknad.innsending.security

import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Component

@Component
class Tilgangskontroll(val pdlService: PdlInterface) {
	val testbrukerid = "02097225454"

	fun hentBrukerFraToken(brukerId: String?): String {
		return brukerId ?: testbrukerid // // TODO endre til alltid å hente fra token
	}

	fun hentPersonIdents(brukerId: String): List<String> {
		return pdlService.hentPersonIdents(brukerId).map { it.ident }
	}

	fun hentPersonIdents(): List<String> {
		val brukerId = hentBrukerFraToken(null)
		return pdlService.hentPersonIdents(brukerId).map { it.ident }
	}

	fun harTilgang(soknadDto: DokumentSoknadDto?) {
		val brukerId = hentBrukerFraToken(null)
		harTilgang(soknadDto, brukerId)
	}

	fun harTilgang(soknadDto: DokumentSoknadDto?, brukerId: String?) {
		val idents = hentPersonIdents(hentBrukerFraToken(brukerId))
		if (idents.contains(soknadDto?.brukerId)) return
		throw ResourceNotFoundException(null, "Søknad finnes ikke eller er ikke tilgjengelig for innlogget bruker")
	}

}
