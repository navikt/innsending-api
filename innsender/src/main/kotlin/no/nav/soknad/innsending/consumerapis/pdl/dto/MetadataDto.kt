package no.nav.soknad.innsending.consumerapis.pdl.dto

data class MetadataDto(
	val master: String,
	val endringer: List<EndringDto>
)

