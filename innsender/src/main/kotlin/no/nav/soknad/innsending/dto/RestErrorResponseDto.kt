package no.nav.soknad.innsending.dto

import java.time.LocalDateTime

data class RestErrorResponseDto(val arsak: String, val message: String, val timeStamp: LocalDateTime, val errorCode: String)
