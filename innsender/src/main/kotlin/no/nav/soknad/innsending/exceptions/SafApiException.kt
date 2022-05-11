package no.nav.soknad.innsending.exceptions

class SafApiException(arsak: String?, message: String): RuntimeException(message) {
	var arsak = arsak
}

