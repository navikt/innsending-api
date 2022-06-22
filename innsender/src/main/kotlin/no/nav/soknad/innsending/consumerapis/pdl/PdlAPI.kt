package no.nav.soknad.innsending.consumerapis.pdl

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import com.expediagroup.graphql.client.types.GraphQLClientError
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.*
import no.nav.soknad.innsending.util.testpersonid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.exceptions.PdlApiException
import no.nav.soknad.innsending.exceptions.SafApiException
import no.nav.soknad.innsending.pdl.generated.HentPersonInfo
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable

@Service
@Profile("test | dev | prod")
@Qualifier("pdl")
class PdlAPI(
	private val pdlGraphQLClient: GraphQLWebClient,
	private val tokenUtil: SubjectHandlerInterface

): PdlInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

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

	override fun hentPersonIdents(brukerId: String): List<IdentDto> = runBlocking {
		try {
			hentPersonInfo()?.hentIdenter?.identer?.map {IdentDto(it.ident, it.gruppe.toString(), it.historisk)}?.toList()
				?: dummyHentBrukerIdenter(brukerId) // TODO fjern
		} catch (ex: Exception) {
			logger.warn(("Henting fra PDL feilet med ${ex.message}. Returnerer pålogget ident"))
			listOf(IdentDto(brukerId,"FOLKEREGISTERIDENT", false))
		}
	}

	override fun hentPersonData(brukerId: String): PersonDto? = runBlocking {

		try {
			hentPersonInfo()?.hentPerson?.navn?.map {PersonDto(brukerId, it.fornavn, it.mellomnavn, it.etternavn)}?.first()
				?: dummyPersonDtos[brukerId] // TODO erstatt med kall til PDL
		} catch (ex: Exception) {
			logger.warn(("Henting fra PDL feilet med ${ex.message}"))
			dummyPersonDtos[brukerId] // TODO erstatt med kall til PDL
		}

	}


	@Cacheable("personInfo")
	suspend fun hentPersonInfo(): HentPersonInfo.Result? {
		val response = pdlGraphQLClient.execute(
			HentPersonInfo(
				HentPersonInfo.Variables(tokenUtil.getUserIdFromToken())
			)
		)
		if (response.data != null) {
			checkForErrors(response.errors)
			return response.data
		} else {
			logger.error("Oppslag mot personregisteret feilet. Fikk feil i kallet til personregisteret")
			throw PdlApiException("Oppslag mot personregisteret feilet", "Fikk feil i kallet til personregisteret")
		}
	}

	private fun checkForErrors(errors: List<GraphQLClientError>?) {
		errors?.let { handleErrors(it) }
	}

	private fun handleErrors(errors: List<GraphQLClientError>) {
		val errorMessage = errors
			.map { "${it.message} (feilkode: ${it.path} ${it.path?.forEach {e-> e.toString() }}" }
			.joinToString(prefix = "Error i respons fra safselvbetjening: ", separator = ", ") { it }
		logger.error("Oppslag mot søknadsarkivet feilet med $errorMessage")
		throw SafApiException("Oppslag mot søknadsarkivet feilet", "Fikk feil i responsen fra søknadsarkivet")
	}


/*** Temporary methods-> ***/
	private fun dummyHentBrukerIdenter(brukerId: String): List<IdentDto> {
		val dummyList = dummyIdents.filter {inneholderBrukerId(brukerId, it)}.toList().flatten()
	return dummyList.ifEmpty { listOf(IdentDto(brukerId,"FOLKEREGISTERIDENT", false)) }
	}

	private fun inneholderBrukerId(brukerId: String, liste: List<IdentDto>): Boolean {
		return liste.any { it.ident == brukerId }
	}

	private val dummyIdents = listOf(
	listOf(IdentDto(testpersonid, "FOLKEREGISTERIDENT", false), IdentDto("12345678902","FOLKEREGISTERIDENT", true)),
	listOf(IdentDto("12345678903", "FOLKEREGISTERIDENT", false)),
	listOf(IdentDto("12345678904", "NPID", false)),
	listOf(IdentDto("12345678906", "FOLKEREGISTERIDENT", false),IdentDto("12345678905", "AKTORID", true))
	)

	private val dummyPersonDtos = mapOf(
		testpersonid to PersonDto(
			testpersonid, "F1", null, "E1"
		),
		"12345678903" to PersonDto(
			"12345678903", "F3", null, "E3"
		),
		"12345678904" to PersonDto(
			"12345678904","F4", null, "E4"
		),
		"12345678905" to PersonDto(
			"12345678905", "F5", null, "E5"
		)
	)
}
