package no.nav.soknad.innsending.dto

import no.nav.soknad.innsending.repository.OpplastingsStatus
import java.time.LocalDateTime

data class VedleggDto(
	val id: Long?,
	val vedleggsnr: String?,
	val tittel: String,
	val uuid: String?,
	val mimetype: String?,
	val document: ByteArray?,
	val erHoveddokument: Boolean,
	val erVariant: Boolean,
	val erPdfa: Boolean,
	val skjemaurl: String?,
	val opplastingsStatus: OpplastingsStatus,
	val opprettetdato: LocalDateTime
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as VedleggDto

		if (id == other.id) return true

		return false
	}

	override fun hashCode(): Int {
		return vedleggsnr.hashCode()
	}

}
