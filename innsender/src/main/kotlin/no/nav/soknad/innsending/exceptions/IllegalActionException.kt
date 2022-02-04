package no.nav.soknad.innsending.exceptions

class IllegalActionException(arsak: String?, message: String): RuntimeException(message) {
	val arsak = arsak
}
