package no.nav.soknad.innsending.exceptions

class IllegalActionException(val arsak: String?, message: String, val errorCode: String = "errorCode.somethingFailedTryLater"): RuntimeException(message)
