package no.nav.soknad.innsending.exceptions

class SanityException(arsak: String?, message: String): RuntimeException(message) {
	var arsak = arsak
}
