package no.nav.soknad.innsending.exceptions

class IllegalActionException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.GENERAL_ERROR
) : RuntimeException(message)
