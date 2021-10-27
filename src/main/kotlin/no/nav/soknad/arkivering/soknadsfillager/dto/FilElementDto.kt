package no.nav.soknad.arkivering.soknadsfillager.dto

import java.time.LocalDateTime

data class FilElementDto(val uuid: String, val fil: ByteArray?, val opprettet: LocalDateTime?) {
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as FilElementDto

		if (uuid != other.uuid) return false

		return true
	}

	override fun hashCode(): Int {
		return uuid.hashCode()
	}
}
