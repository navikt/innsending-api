package no.nav.soknad.innsending.exceptions

class SafApiException(var arsak: String?, message: String): RuntimeException(message)

