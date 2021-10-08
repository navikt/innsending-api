package no.nav.soknad.innsending.dto

import no.nav.soknad.innsending.repository.OpplastingsStatus
import java.time.LocalDateTime

data class VedleggDto(
	val id: Long?,
	val vedleggsnr: String?,
	val tittel: String,
	val skjemaurl: String?,
	val uuid: String?,
	val mimetype: String?,
	val document: ByteArray?,
	val erHoveddokument: Boolean,
	val erVariant: Boolean,
	val erPdfa: Boolean,
	val opplastingsStatus: OpplastingsStatus,
	val opprettetdato: LocalDateTime
)
