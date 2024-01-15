package no.nav.soknad.innsending.util.logging

import org.slf4j.Logger


class CombinedLogger(private val logger: Logger, private val secureLogger: Logger) {

	// Log both normal and secure log (with userId)
	fun log(melding: String, brukerId: String) {
		logger.info(melding)
		secureLogger.info("[$brukerId] $melding")
	}
}
