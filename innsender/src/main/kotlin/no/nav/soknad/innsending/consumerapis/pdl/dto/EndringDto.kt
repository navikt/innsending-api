package no.nav.soknad.innsending.consumerapis.pdl.dto

import java.time.LocalDateTime

data class EndringDto(
	val kilde: String,
	val registrert: LocalDateTime,
	val type: String
)
