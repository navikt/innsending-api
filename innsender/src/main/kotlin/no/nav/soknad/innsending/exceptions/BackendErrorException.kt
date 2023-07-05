package no.nav.soknad.innsending.exceptions

class BackendErrorException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
) : RuntimeException(message)
