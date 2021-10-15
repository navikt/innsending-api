package no.nav.soknad.innsending.dto

import no.nav.soknad.innsending.repository.OpplastingsStatus

data class SkjemaDokumentDto(
	val vedleggsnr: String,
	val tittel: String,
	val mimetype: String?,
	val document: ByteArray?,
	val erHoveddokument: Boolean,
	val erVariant: Boolean,
	val erPdfa: Boolean?,
	val opplastingsStatus: OpplastingsStatusDto
)
