package no.nav.soknad.innsending.rest.validering

import org.jboss.logging.Logger

private val logger = Logger.getLogger("no.nav.soknad.innsending.rest.validering.InputValidering")

fun removeInvalidControlCharacters(key: String, input: String?): String? {
	if (input == null) return null
	val output = input.filterNot { ch -> ch == '\u0000' || (ch.isISOControl() && ch != '\n' && ch != '\r' && ch != '\t') }
	if (output.length != input.length) logger.warn("Removed invalid control characters from input: $key")
	return output
}
