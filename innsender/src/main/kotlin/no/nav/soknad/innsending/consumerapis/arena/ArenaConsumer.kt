package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.arena.dto.Maalgruppe
import no.nav.soknad.innsending.exceptions.*
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Constants.NAV_PERSON_IDENT
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodyOrNull

@Component
@Profile("test | dev | prod")
class ArenaConsumer(
	@Qualifier("arenaClient") private val webClient: WebClient,
	@Value("\$spring.application.name}") private val applicationName: String,
	private val subjectHandler: SubjectHandlerInterface,
	private val restConfig: RestConfig,
) : ArenaConsumerInterface {

	private val logger: Logger = LoggerFactory.getLogger(javaClass)

	override suspend fun getMaalgruppe(): List<Maalgruppe> {
		logger.info("Henter målgruppe")

		return webClient
			.get()
			.uri("${restConfig.arenaUrl}/api/v1/maalgrupper?fom=2023-01-01") // FIXME: Add correct date
			.accept(MediaType.APPLICATION_JSON)
			.header(NAV_PERSON_IDENT, subjectHandler.getUserIdFromToken())
			.retrieve()
			.awaitBodyOrNull()
			?: throw BackendErrorException(
				message = "Kunne ikke hente målgruppe"
			)
	}

	private fun handleError(error: Throwable, serviceName: String) {
		if (error is WebClientResponseException) {
			val statusCode = error.statusCode
			val responseBody = error.responseBodyAsString
			val errorMessage =
				String.format("Kall mot %s feilet (statuskode: %s). Body: %s", serviceName, statusCode, responseBody)

			if (statusCode == HttpStatus.UNAUTHORIZED) {
				throw ClientErrorUnauthorizedException(errorMessage, error, ErrorCode.ARENA_UNAUTHORIZED)
			}

			if (statusCode == HttpStatus.FORBIDDEN) {
				throw ClientErrorForbiddenException(errorMessage, error, ErrorCode.ARENA_FORBIDDEN)
			}

			if (statusCode.is4xxClientError) {
				throw IllegalActionException(errorMessage, error, ErrorCode.GENERAL_ERROR)
			}

			throw BackendErrorException(errorMessage, error, ErrorCode.GENERAL_ERROR)
		}
	}
}
