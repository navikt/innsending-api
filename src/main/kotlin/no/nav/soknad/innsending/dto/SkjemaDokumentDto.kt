package no.nav.soknad.innsending.dto

data class SkjemaDokumentDto(
	val vedleggsnr: String,
	val tittel: String,
	val mimetype: String?,
	val document: ByteArray?,
	val erHoveddokument: Boolean,
	val erVariant: Boolean,
	val erPdfa: Boolean?,
	val opplastingsStatus: OpplastingsStatusDto
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as SkjemaDokumentDto

		if (vedleggsnr == other.vedleggsnr && erHoveddokument == other.erHoveddokument
			&& erVariant == other.erVariant && mimetype.equals(other.mimetype)) return true

		return false
	}

	override fun hashCode(): Int {
		return vedleggsnr.hashCode() + mimetype.hashCode()
	}
}
