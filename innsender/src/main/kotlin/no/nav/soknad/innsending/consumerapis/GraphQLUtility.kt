package no.nav.soknad.innsending.consumerapis

import com.expediagroup.graphql.client.types.GraphQLClientError
import no.nav.soknad.innsending.exceptions.BackendErrorException
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("no.nav.soknad.innsending.consumerapis.GrapQLUtility")

fun handleErrors(errors: List<GraphQLClientError>, system: String) {
	val errorMessages = errors.joinToString(separator = ", ") { "'${it.message}' (endepunkt: ${it.path})" }
	logger.warn("Feil i responsen fra $system (antall feil ${errors.size}): $errorMessages")
	throw BackendErrorException("Feil i responsen fra $system.")
}
