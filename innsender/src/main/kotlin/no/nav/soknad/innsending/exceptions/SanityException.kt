package no.nav.soknad.innsending.exceptions

class SanityException(var arsak: String?, message: String, val errorCode: String = "errorCode.somethingFailedTryLater"): RuntimeException(message)
