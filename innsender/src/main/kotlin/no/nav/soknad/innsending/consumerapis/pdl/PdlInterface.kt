package no.nav.soknad.innsending.consumerapis.pdl

import no.nav.soknad.innsending.consumerapis.pdl.dto.IdentDto
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto

interface PdlInterface {

	fun hentPersonData(brukerId: String): PersonDto?

	fun hentPersonIdents(brukerId: String): List<IdentDto>

}
