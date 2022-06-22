package no.nav.soknad.innsending.exceptions

class SanityException(var arsak: String?, message: String): RuntimeException(message)
