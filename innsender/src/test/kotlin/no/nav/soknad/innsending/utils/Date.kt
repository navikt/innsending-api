package no.nav.soknad.innsending.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Date {
	fun formatToLocalDate(date: LocalDateTime): String {
		val formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd")
		return date.format(formatter)
	}

	fun formatToLocalDateTime(date: LocalDateTime): String {
		val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
		return date.format(formatter)
	}
}
