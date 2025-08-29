package no.nav.soknad.innsending.exceptions

class ServiceUnavailableException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
) : RuntimeException(message)
