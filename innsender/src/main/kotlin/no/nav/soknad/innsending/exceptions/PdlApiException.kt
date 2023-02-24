package no.nav.soknad.innsending.exceptions

class PdlApiException(
	var arsak: String?,
	message: String,
	val errorCode: String = "errorCode.somethingFailedTryLater"
) : RuntimeException(message)
