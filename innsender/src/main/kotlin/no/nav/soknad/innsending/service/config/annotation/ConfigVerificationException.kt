package no.nav.soknad.innsending.service.config.annotation

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.service.config.ConfigDefinition
import org.springframework.http.HttpStatus

class ConfigVerificationException(
	override val message: String,
	val configuration: ConfigDefinition,
	val httpStatus: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
	val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
) : RuntimeException(message)
