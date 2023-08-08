package no.nav.soknad.innsending.exceptions

class ResourceNotFoundException(
	var arsak: String? = null,
	message: String,
	val errorCode: String = "errorCode.somethingFailedTryLater"
) : RuntimeException(message)
