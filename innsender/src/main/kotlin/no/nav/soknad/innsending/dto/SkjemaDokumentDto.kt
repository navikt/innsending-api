package no.nav.soknad.innsending.dto

data class SkjemaDokumentDto(
	val vedleggsnr: String,
	val tittel: String,
	val label: String,
	val beskrivelse: String?,
	val mimetype: String?,
	val pakrevd: Boolean,
	val document: ByteArray?,
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as SkjemaDokumentDto

		if (vedleggsnr == other.vedleggsnr && mimetype.equals(other.mimetype) && document.contentEquals(other.document)) return true

		return false
	}

	override fun hashCode(): Int {
		return vedleggsnr.hashCode() + mimetype.hashCode()
	}
}
