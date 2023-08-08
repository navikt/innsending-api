package no.nav.soknad.innsending.dto

import java.time.LocalDateTime

data class RestErrorResponseDto(
	val message: String,
	val timeStamp: LocalDateTime,
	val errorCode: String
)
