package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.Constants.HEADER_CALL_ID
import no.nav.soknad.innsending.util.Constants.NAV_PERSON_IDENT
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate

@Component
@Profile("test | dev | prod")
class ArenaConsumer(
	@Qualifier("arenaClient") private val webClient: WebClient,
	private val subjectHandler: SubjectHandlerInterface,
	private val restConfig: RestConfig,
) : ArenaConsumerInterface {

	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val fromDate = LocalDate.now().minusMonths(6)
	private val toDate = LocalDate.now().plusMonths(2)

	override suspend fun getMaalgrupper(): List<Maalgruppe> {
		val innsendingsId = MDC.get(Constants.MDC_INNSENDINGS_ID)
		logger.info("Henter målgruppe for innsendingsId: {}", innsendingsId)

		val uri = UriComponentsBuilder
			.fromUriString("${restConfig.arenaUrl}/api/v1/maalgrupper")
			.queryParam("fom", fromDate)
			.queryParam("tom", toDate)
			.build()
			.toUri()

		return webClient
			.get()
			.uri(uri)
			.accept(MediaType.APPLICATION_JSON)
			.header(HEADER_CALL_ID, innsendingsId)
			.header(NAV_PERSON_IDENT, subjectHandler.getUserIdFromToken())
			.retrieve()
			.awaitBodyOrNull()
			?: throw BackendErrorException(
				message = "Kunne ikke hente målgruppe"
			)
	}

	override suspend fun getAktiviteter(): List<Aktivitet> {
		val innsendingsId = MDC.get(Constants.MDC_INNSENDINGS_ID)
		logger.info("Henter målgruppe for innsendingsId: {}", innsendingsId)

		val uri = UriComponentsBuilder
			.fromUriString("${restConfig.arenaUrl}/api/v1/tilleggsstoenad/dagligreise")
			.queryParam("fom", fromDate)
			.queryParam("tom", toDate)
			.build()
			.toUri()

		return webClient
			.get()
			.uri(uri)
			.accept(MediaType.APPLICATION_JSON)
			.header(HEADER_CALL_ID, innsendingsId)
			.header(NAV_PERSON_IDENT, subjectHandler.getUserIdFromToken())
			.retrieve()
			.awaitBodyOrNull()
			?: throw BackendErrorException(
				message = "Kunne ikke hente aktiviteter"
			)
	}
}
