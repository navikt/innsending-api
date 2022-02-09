package no.nav.soknad.innsending.consumerapis.pdl

import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonIdent

interface PdlInterface {

	fun hentPersonData(brukerId: String): PersonDto?

	fun hentPersonIdents(brukerId: String): List<PersonIdent>

}
