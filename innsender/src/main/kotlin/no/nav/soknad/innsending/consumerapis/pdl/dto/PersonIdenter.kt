package no.nav.soknad.innsending.consumerapis.pdl.dto

import no.nav.soknad.innsending.exceptions.PdlApiException

data class PersonIdent (
	val ident: String,
	val gruppe: String,
	val historisk: Boolean
)

fun toPersonIdenter(personResponse: PersonResponse): List<PersonIdent> {
	if (personResponse.errors != null) throw PdlApiException("Henting av brukerdata fra PDL feilet", personResponse.errors.message)

	val ids = personResponse.personData.identifikatorer
	if (ids == null || ids.isEmpty()) throw PdlApiException("Fant ingen ident knyttet til pålogget bruker i PDL", "")

	return ids.map { toPersonIdent(it, firstInList() ) }.toList()
}
private var first: Boolean = true
fun firstInList(): Boolean {
	if (first) {
		first = false
		return true
	}
	return false
}

fun toPersonIdent(folkeregisterIdent: Folkeregisteridentifikator, first: Boolean) =
	PersonIdent(folkeregisterIdent.identifikasjonsnummer, folkeregisterIdent.type, !first)
