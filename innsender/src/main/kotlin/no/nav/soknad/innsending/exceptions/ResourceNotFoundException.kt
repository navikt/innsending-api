package no.nav.soknad.innsending.exceptions

class ResourceNotFoundException(var arsak: String?, message: String): RuntimeException(message)
