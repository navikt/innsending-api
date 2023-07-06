package no.nav.soknad.innsending.supervision.requestlogger

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class LogRequest(vararg val logParameters: String)
