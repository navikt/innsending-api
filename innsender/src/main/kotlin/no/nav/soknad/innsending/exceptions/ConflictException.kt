package no.nav.soknad.innsending.exceptions

class ConflictException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.CONFLICT
) : RuntimeException(message)
