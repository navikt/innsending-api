package no.nav.soknad.innsending.exceptions

class BackendErrorException(
	val arsak: String? = null,
	override val message: String,
	val errorCode: String = "errorCode.somethingFailedTryLater"
) : RuntimeException(message)
