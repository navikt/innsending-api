package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.exceptions.ErrorCode

// Will continue execution if the exception is caught
class ArenaException(
	override val message: String,
	override val cause: Throwable? = null,
	val errorCode: ErrorCode = ErrorCode.ARENA_ERROR
) : RuntimeException(message)

