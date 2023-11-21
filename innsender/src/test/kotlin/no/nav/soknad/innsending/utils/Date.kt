package no.nav.soknad.innsending.utils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Date {
	fun formatDate(date: LocalDateTime): String {
		val formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd")
		return date.format(formatter)
	}
}
