package no.nav.soknad.innsending.exceptions

class ResourceNotFoundException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.NOT_FOUND
) : RuntimeException(message)
