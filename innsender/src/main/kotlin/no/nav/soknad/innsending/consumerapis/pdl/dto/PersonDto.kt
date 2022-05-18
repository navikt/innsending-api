package no.nav.soknad.innsending.consumerapis.pdl.dto

data class PersonDto(
	val ident: String,
	val fornavn: String,
	val mellomnavn: String? = null,
	val etternavn: String
)
