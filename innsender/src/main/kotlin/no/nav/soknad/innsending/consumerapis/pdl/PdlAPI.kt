package no.nav.soknad.innsending.consumerapis.pdl

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.*
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.util.testpersonid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import io.github.resilience4j.kotlin.retry.executeFunction
import io.github.resilience4j.retry.Retry
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
@Profile("dev | prod")
@Qualifier("pdl")
class PdlAPI(
	private val restConfig: RestConfig,
	private val pdlWebClient: WebClient,
//	private val stsClient: StsClient,
	private val retryPdl: Retry,
	private val tokenUtil: SubjectHandlerInterface

): PdlInterface, HealthRequestInterface {

	override fun ping(): String {
		//healthApi.ping()
		return "pong"
	}
	override fun isReady(): String {
		//healthApi.isReady()
		return "ok"
	}
	override fun isAlive(): String {
		//healthApi.isAlive()
		return "ok"
	}

	override fun hentPersonData(brukerId: String): PersonDto? {
		val personDto = dummyPersonDtos.get(brukerId) // TODO erstatt med kall til PDL
		if (personDto != null) return personDto
		throw BackendErrorException("Pålogget bruker $brukerId ikke funnet i PDL", "Problem med å hente opp brukerdata")
	}

	override fun hentPersonIdents(brukerId: String): List<PersonIdent> {
		return dummyHentBrukerIdenter(brukerId)
	}

	private fun getPersonInfo(): PersonResponse {
		var results = PersonResponse(PersonDto(null, null), null)

		runCatching {
			retryPdl.executeFunction {

				results = pdlWebClient.post()
					.header(HttpHeaders.AUTHORIZATION, "Bearer ${tokenUtil.getToken()}")
					//.header("Nav-Consumer-Token", "Bearer ${stsClient.oidcToken()}")
					.bodyValue(hentPersonQuery(tokenUtil.getUserIdFromToken(), true))
					.retrieve()
					.bodyToMono<PersonResponse>()
					.block() ?: throw RuntimeException("Person not found")

			}
		}.onFailure {
			throw RuntimeException("PDL could not be reached")
		}

		return results
	}



/*** Temporary methods-> ***/
	private fun dummyHentBrukerIdenter(brukerId: String): List<PersonIdent> {
		return dummyIdents.filter {inneholderBrukerId(brukerId, it)}.toList().flatten()
	}

	private fun inneholderBrukerId(brukerId: String, liste: List<PersonIdent>): Boolean {
		return liste.any { it.ident == brukerId }
	}

	private val dummyIdents = listOf(
	listOf(PersonIdent(testpersonid, "FOLKEREGISTERIDENT", false), PersonIdent("12345678902","FOLKEREGISTERIDENT", true)),
	listOf(PersonIdent("12345678903", "FOLKEREGISTERIDENT", false)),
	listOf(PersonIdent("12345678904", "NPID", false)),
	listOf(PersonIdent("12345678906", "FOLKEREGISTERIDENT", false),PersonIdent("12345678905", "AKTORID", true))
	)

	private val dummyPersonDtos = mapOf(
		testpersonid to PersonDto(
			listOf(Navn("F1", null, "E1")),
			listOf(Folkeregisteridentifikator(testpersonid, "FOLKEREGISTERIDENT", "gjeldende"),
				Folkeregisteridentifikator("12345678902", "FOLKEREGISTERIDENT", "historisk"))
		),
		"12345678903" to PersonDto(
			listOf(Navn("F3", null, "E3")),
			listOf(Folkeregisteridentifikator("12345678903", "FOLKEREGISTERET", "gjeldende"))
		),
		"12345678904" to PersonDto(
			listOf(Navn("F4", null, "E4")),
			listOf(Folkeregisteridentifikator("12345678903", "FOLKEREGISTERET", "gjeldende"))
		),
		"12345678905" to PersonDto(
			listOf(Navn("F5", null, "E5")),
			listOf(Folkeregisteridentifikator("12345678905", "FOLKEREGISTERET", "gjeldende"),
				Folkeregisteridentifikator("12345678906", "FOLKEREGISTERET", "historisk"))
		)
	)
}
