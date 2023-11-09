package no.nav.soknad.innsending.exceptions

class SoknadAlreadyExistsException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.SOKNAD_ALREADY_EXISTS
) : RuntimeException(message)
