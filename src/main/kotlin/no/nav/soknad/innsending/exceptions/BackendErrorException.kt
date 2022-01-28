package no.nav.soknad.innsending.exceptions

class BackendErrorException(arsak: String?, message: String): RuntimeException(message) {
	val arsak = arsak
}
