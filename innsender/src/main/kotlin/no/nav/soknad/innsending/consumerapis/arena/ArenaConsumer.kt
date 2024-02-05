package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.NonCriticalException
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Constants.HEADER_CALL_ID
import no.nav.soknad.innsending.util.Constants.NAV_PERSON_IDENT
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import org.springframework.web.util.UriComponentsBuilder
import reactor.core.publisher.Mono
import java.time.LocalDate

@Component
@Profile("test | dev | prod")
class ArenaConsumer(
	@Qualifier("arenaClient") private val webClient: WebClient,
	private val subjectHandler: SubjectHandlerInterface,
	private val restConfig: RestConfig,
) : ArenaConsumerInterface {

	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")

	private val fromDate = LocalDate.now().minusMonths(6)
	private val toDate = LocalDate.now().plusMonths(2)

	override suspend fun getMaalgrupper(): List<Maalgruppe> {
		val callId = MDCUtil.callIdOrNew()
		val userId = subjectHandler.getUserIdFromToken()

		logger.info("Henter målgruppe med callId:{}", callId)
		secureLogger.info("[{}] Henter målgrupper for callId:{}", userId, callId)

		val uri = UriComponentsBuilder
			.fromUriString("${restConfig.arenaUrl}/api/v1/maalgrupper")
			.queryParam("fom", fromDate)
			.queryParam("tom", toDate)
			.build()
			.toUri()

		val maalgrupper: List<Maalgruppe> = webClient
			.get()
			.uri(uri)
			.accept(MediaType.APPLICATION_JSON)
			.header(HEADER_CALL_ID, callId)
			.header(NAV_PERSON_IDENT, userId)
			.retrieve()
			.onStatus({ status -> status.is4xxClientError || status.is5xxServerError },
				{ clientResponse ->
					handleErrorResponse(clientResponse, "Feil ved henting av målgrupper")
				})
			.awaitBodyOrNull() ?: throw NonCriticalException(message = "Kunne ikke hente målgrupper")

		secureLogger.info("[{}] Målgrupper: {}", userId, maalgrupper.toString())

		return maalgrupper
	}

	override suspend fun getAktiviteter(): List<Aktivitet> {
		val callId = MDCUtil.callIdOrNew()
		val userId = subjectHandler.getUserIdFromToken()

		logger.info("Henter aktiviteter for callId:{}", callId)
		secureLogger.info("[{}] Henter aktiviteter for callId:{}", userId, callId)

		val uri = UriComponentsBuilder
			.fromUriString("${restConfig.arenaUrl}/api/v1/tilleggsstoenad/dagligreise")
			.queryParam("fom", fromDate)
			.queryParam("tom", toDate)
			.build()
			.toUri()

		val aktiviteter: List<Aktivitet> = webClient
			.get()
			.uri(uri)
			.accept(MediaType.APPLICATION_JSON)
			.header(HEADER_CALL_ID, callId)
			.header(NAV_PERSON_IDENT, userId)
			.retrieve()
			.onStatus({ status -> status.is4xxClientError || status.is5xxServerError },
				{ clientResponse ->
					handleErrorResponse(clientResponse, "Feil ved henting av aktiviteter")
				})
			.awaitBodyOrNull()
			?: emptyList()

		secureLogger.info("[{}] Aktiviteter: {}", userId, aktiviteter.toString())

		return aktiviteter
	}

	private fun handleErrorResponse(clientResponse: ClientResponse, errorMessage: String): Mono<Exception> {
		val status = clientResponse.statusCode()
		val message = "${status.value()}: Arena consumer feil - $errorMessage"

		logger.warn(message)

		if (status.is4xxClientError) {
			throw NonCriticalException(message = message)
		} else {
			throw BackendErrorException(message = message)
		}
	}
}
