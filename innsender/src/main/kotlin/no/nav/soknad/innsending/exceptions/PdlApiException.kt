package no.nav.soknad.innsending.exceptions

class PdlApiException(var arsak: String?, message: String): RuntimeException(message)
