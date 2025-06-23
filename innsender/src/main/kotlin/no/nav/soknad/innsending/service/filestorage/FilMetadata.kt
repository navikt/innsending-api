package no.nav.soknad.innsending.service.filestorage

data class FilMetadata(
	val filId: String,
	val filnavn: String,
	val storrelse: Int,
	val vedleggId: String,
	val innsendingId: String,
	val filtype: String,
)
