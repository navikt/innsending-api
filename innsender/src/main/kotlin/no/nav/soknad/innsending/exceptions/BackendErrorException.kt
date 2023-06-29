package no.nav.soknad.innsending.exceptions

class BackendErrorException(
	val arsak: String? = null,
	message: String,
	val errorCode: String = "errorCode.somethingFailedTryLater"
) : RuntimeException(message)
