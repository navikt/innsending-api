package no.nav.soknad.innsending.exceptions

import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ExceptionHelper(private val innsenderMetrics: InnsenderMetrics) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun reportException(e: Exception, operation: String, tema: String) {
		logger.error("Feil ved operasjon $operation", e)
		innsenderMetrics.operationsErrorCounterInc(operation, tema)
	}
}
