package no.nav.soknad.innsending.consumerapis

import com.expediagroup.graphql.client.types.GraphQLClientError
import no.nav.soknad.innsending.exceptions.PdlApiException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("no.nav.soknad.innsending.GrapQLUtility")

fun handleErrors(errors: List<GraphQLClientError>, system: String) {
	val errorMessage = errors
		.map { "${it.message} (feilkode: ${it.path} ${it.path?.forEach {e-> e.toString() }}" }
		.joinToString(prefix = "Error i respons fra $system: ", separator = ", ") { it }
	logger.error("Oppslag mot $system feilet med $errorMessage")
	throw PdlApiException("Oppslag mot $system feilet", "Fikk feil i responsen fra $system")
}