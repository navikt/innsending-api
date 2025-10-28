package no.nav.soknad.innsending.service.fillager

data class Fil(
	val innhold: ByteArray,
	val metadata: FilMetadata,
) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as Fil

		if (!innhold.contentEquals(other.innhold)) return false
		if (metadata != other.metadata) return false

		return true
	}

	override fun hashCode(): Int {
		var result = innhold.contentHashCode()
		result = 31 * result + metadata.hashCode()
		return result
	}
}
