package no.nav.soknad.innsending.exceptions

class GeneralException(message: String, errorCode: String = "errorCode.somethingFailedTryLater") :
	RuntimeException(message) {
}
