package no.nav.soknad.innsending.dto

import java.time.LocalDateTime

data class FilDto(val id: Long?, val vedleggsid: Long, val filnavn: String, val mimetype: String, val data: ByteArray?, val opprettetdato: LocalDateTime?) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as FilDto

		if (id == other.id) return true

		return false
	}

	override fun hashCode(): Int {
		return id.hashCode() + vedleggsid.hashCode()
	}

}
