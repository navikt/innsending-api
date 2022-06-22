package no.nav.soknad.innsending.exceptions

class BackendErrorException(val arsak: String?, message: String): RuntimeException(message)
