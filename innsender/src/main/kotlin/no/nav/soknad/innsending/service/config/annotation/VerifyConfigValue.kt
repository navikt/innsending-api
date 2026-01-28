package no.nav.soknad.innsending.service.config.annotation

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.service.config.ConfigDefinition
import org.springframework.http.HttpStatus

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class VerifyConfigValue(
	val config: ConfigDefinition,
	val value: String,
	val httpStatus: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
	val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR,
	val message: String = "Konfigurasjonsverdi er ikke som forventet",
)
