package no.nav.soknad.innsending.exceptions

class ForbiddenException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.FORBIDDEN
) : RuntimeException(message)
