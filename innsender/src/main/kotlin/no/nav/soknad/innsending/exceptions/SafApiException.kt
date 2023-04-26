package no.nav.soknad.innsending.exceptions

class SafApiException(
	var arsak: String?,
	message: String,
	val errorCode: String = "errorCode.somethingFailedTryLater"
) : RuntimeException(message)

