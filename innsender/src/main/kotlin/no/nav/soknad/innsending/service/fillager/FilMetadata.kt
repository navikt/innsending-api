package no.nav.soknad.innsending.service.fillager

data class FilMetadata(
	val filId: String,
	val vedleggId: String,
	val innsendingId: String,
	val filnavn: String,
	val storrelse: Int,
	val filtype: String,
	val status: FilStatus,
)
