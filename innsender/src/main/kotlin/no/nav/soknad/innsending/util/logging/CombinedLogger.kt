package no.nav.soknad.innsending.util.logging

import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.MarkerFactory


class CombinedLogger(private val logger: Logger) {

	private val secureLogsMarker: Marker = MarkerFactory.getMarker("TEAM_LOGS")

	// Log both normal and secure log (with userId)
	fun log(melding: String, brukerId: String) {
		logger.info(melding)
		logger.info(secureLogsMarker, "[$brukerId] $melding")
	}
}
