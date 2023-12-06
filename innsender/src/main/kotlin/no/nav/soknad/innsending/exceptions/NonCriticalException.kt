package no.nav.soknad.innsending.exceptions


class NonCriticalException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.NON_CRITICAL
) : RuntimeException(message)

