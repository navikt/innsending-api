package no.nav.soknad.innsending.supervision.timer

import no.nav.soknad.innsending.supervision.InnsenderOperation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Timed(val operation: InnsenderOperation)
