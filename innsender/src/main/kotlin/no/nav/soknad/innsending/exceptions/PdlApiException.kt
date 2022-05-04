package no.nav.soknad.innsending.exceptions

class PdlApiException(arsak: String?, message: String): RuntimeException(message) {
	var arsak = arsak
}
