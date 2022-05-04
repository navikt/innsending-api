package no.nav.soknad.innsending.consumerapis.pdl.dto

data class PersonResponse(
	val personData: PersonDto,
	val errors: PdlError?

)
data class PersonDto (
	val navn: List<Navn>?,
	val identifikatorer: List<Folkeregisteridentifikator>?
)

data class Navn(
	val fornavn: String,
	val mellomnavn: String?,
	val etternavn: String
)

data class Folkeregisteridentifikator(
	val identifikasjonsnummer: String,
	val type: String,
	val status: String
)

data class PdlError(
	val message: String,
	val locations: List<PdlErrorLocation>,
	val path: List<String>?,
	val extensions: PdlErrorExtension
)

data class PdlErrorLocation(
	val line: Int?,
	val column: Int?
)

data class PdlErrorExtension(
	val code: String?,
	val classification: String
)
