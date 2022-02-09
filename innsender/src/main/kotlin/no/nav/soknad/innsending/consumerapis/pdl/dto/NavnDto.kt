package no.nav.soknad.innsending.consumerapis.pdl.dto

data class NavnDto(
	val fornavn: String,
	val mellomnavn: String?,
	val etternavn: String,
	val metadata: MetadataDto,
	val folkeregistermetadata: FolkeregisterMetadataDto?,
)
