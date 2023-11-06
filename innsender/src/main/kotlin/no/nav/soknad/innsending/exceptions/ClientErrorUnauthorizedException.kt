package no.nav.soknad.innsending.exceptions

// Errors som skal gi 401 respons
open class ClientErrorUnauthorizedException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
) : RuntimeException(message)
