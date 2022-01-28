package no.nav.soknad.innsending.exceptions

class ResourceNotFoundException(arsak: String?, message: String): RuntimeException(message) {
	var arsak = arsak
}
