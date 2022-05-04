package no.nav.soknad.innsending.consumerapis.pdl.dto

import no.nav.soknad.innsending.exceptions.PdlApiException

data class BrukerDto(
	val ident: Folkeregisteridentifikator,
	val navn: Navn
)

fun toBrukerDto(personResponse: PersonResponse): BrukerDto {
	if (personResponse.errors != null) throw PdlApiException("Henting av brukerdata fra PDL feilet", personResponse.errors.message)

	val id = personResponse.personData.identifikatorer?.firstOrNull()
	if (id == null) throw PdlApiException("Fant ingen ident knyttet til pålogget bruker i PDL", "")
	val navn = personResponse.personData.navn?.firstOrNull()
	if (navn == null) throw PdlApiException("Fant ingen navn knyttet til pålogget bruker i PDL", "")

	return BrukerDto(id, navn)
}
