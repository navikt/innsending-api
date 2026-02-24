package no.nav.soknad.innsending.service.fillager

import no.nav.soknad.innsending.model.Mimetype
import java.time.OffsetDateTime

data class FilMetadata(
	val filId: String,
	val vedleggId: String,
	val innsendingId: String,
	val filnavn: String,
	val storrelse: Int,
	val filtype: String,
	val status: FilStatus,
	val mimetype: Mimetype?,
	val createdAt: OffsetDateTime,
)

typealias FileMetadata = FilMetadata
