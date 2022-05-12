package no.nav.soknad.innsending.consumerapis.saf

import com.netflix.graphql.dgs.client.GraphQLError
import com.netflix.graphql.dgs.client.GraphQLResponse
import io.github.resilience4j.kotlin.retry.executeFunction
import io.github.resilience4j.retry.Retry
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.HentPersonVariabler
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonGraphqlQuery
import no.nav.soknad.innsending.consumerapis.saf.SafApiQuery.HENT_SOKNADER_QUERY
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.consumerapis.saf.dto.Dokument
import no.nav.soknad.innsending.exceptions.SafApiException
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.service.Utilities
import no.nav.soknad.innsending.util.testpersonid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Profile("dev | prod")
@Qualifier("saf")
class SafAPI(
	private val safWebClient: WebClient,
	private val retrySaf: Retry,
	private val tokenUtil: SubjectHandlerInterface
): SafInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun ping(): String {
//		healthApi.ping()
		return "pong"
	}
	override fun isReady(): String {
//		healthApi.isReady()
		return "ok"
	}

	override fun isAlive(): String {
//		healthApi.isReady()
		return "ok"
	}

	override fun hentBrukersSakerIArkivet(brukerId: String): List<ArkiverteSaker>? {
		try {
			val response = getSoknadsDataForPerson()
			logger.info("Hentet fra safselvbetjening ${response.data.keys}")
/*
			return getSoknadsDataForPerson()?.extractValueAsObject("dokumentoversiktSelvbetjening", ArkiverteSaker[]::class.java)?.toList() ?:
				dummyArkiverteSoknader[brukerId] // TODO fjern
*/
		} catch (ex: Exception) {
			logger.warn("Henting fra SAF feilet med ${ex.message}")
		}
		return dummyArkiverteSoknader[brukerId] // TODO fjern
	}


	private fun getSoknadsDataForPerson(): GraphQLResponse {
		var results = GraphQLResponse("")

		runCatching {
			retrySaf.executeFunction {

				val response = safWebClient.post()
					.header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenUtil.getToken()}")
					.bodyValue(hentPersonQuery(tokenUtil.getUserIdFromToken(), true))
					.retrieve()
					.bodyToMono<GraphQLResponse>()
					.block() ?: throw SafApiException("Oppslag mot søknadsarkivet feilet", "Fant ikke søknadsdata for pålogget person")

				checkForErrors(response)
				results = response

			}
		}.onFailure {
			throw SafApiException("Oppslag mot søknadsarkivet feilet", "Fant ikke søknadsdata for pålogget person")
		}
		return results
	}

	private fun checkForErrors(results: GraphQLResponse) {
		val errors = results.errors
		errors?.let { handleErrors(it) }
	}

	private fun handleErrors(errors: List<GraphQLError>) {
		val errorMessage = errors
			.map { "${it.message} (feilkode: ${it.path} ${it.pathAsString})" }
			.joinToString(prefix = "Error i respons fra safselvbetjening: ", separator = ", ") { it }
		throw SafApiException("Oppslag mot søknadsarkivet feilet", errorMessage)
	}

	fun hentPersonQuery(fnr: String, historikk: Boolean): PersonGraphqlQuery {
		val query = HENT_SOKNADER_QUERY.replace("[\n\r]", "")
		return PersonGraphqlQuery(query, HentPersonVariabler(fnr, historikk))
	}

	// ***** Midlertidig dummy data -> **********

	private val date = DateTimeFormatter.ISO_DATE_TIME.format( LocalDateTime.now())

	private val dummyArkiverteSoknader = mapOf (
		testpersonid to listOf (
			ArkiverteSaker(Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			)),
			ArkiverteSaker(Utilities.laginnsendingsId(), "Ettersending til test søknad", "BID", date
				, listOf(
					Dokument("NAVe 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
					Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
				))
		),
		"12345678902" to listOf(ArkiverteSaker(Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			))),
		"12345678903" to listOf(ArkiverteSaker(Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			)))
	)
}
