package no.nav.soknad.innsending.consumerapis.pdl

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import com.expediagroup.graphql.client.types.GraphQLClientError
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.handleErrors
import no.nav.soknad.innsending.consumerapis.pdl.dto.IdentDto
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.pdl.generated.HentIdenter
import no.nav.soknad.innsending.pdl.generated.HentPerson
import no.nav.soknad.innsending.pdl.generated.PrefillData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("test | dev | prod")
@Qualifier("pdl")
class PdlAPI(
	private val pdlGraphQLClient: GraphQLWebClient
) : PdlInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun ping(): String {
		return "pong"
	}

	override fun isReady(): String {
		// Ikke implementert kall mot PDL for å sjekke om tjenesten er oppe.
		return "ok"
	}

	override fun isAlive(): String {
		return "ok"
	}

	override fun hentPersonIdents(brukerId: String): List<IdentDto> = runBlocking {
		logger.info("Skal hente en personsidenter fra PDL")
		try {
			hentIdenter(brukerId)?.hentIdenter?.identer?.map { IdentDto(it.ident, it.gruppe.toString(), it.historisk) }
				?: listOf(IdentDto(brukerId, "FOLKEREGISTERIDENT", false))
		} catch (ex: Exception) {
			logger.warn(("Henting fra PDL feilet med ${ex.message}. Returnerer pålogget ident"))
			listOf(IdentDto(brukerId, "FOLKEREGISTERIDENT", false))
		}
	}

	override fun hentPersonData(brukerId: String): PersonDto? = runBlocking {
		try {
			hentPerson(brukerId)?.hentPerson?.navn?.map { PersonDto(brukerId, it.fornavn, it.mellomnavn, it.etternavn) }
				?.first()
		} catch (ex: Exception) {
			logger.warn(("Henting fra PDL feilet med ${ex.message}"))
			null
		}
	}


	@Cacheable("hentPerson")
	suspend fun hentPerson(ident: String): HentPerson.Result? {
		val response = pdlGraphQLClient.execute(
			HentPerson(
				HentPerson.Variables(ident)
			)
		)
		if (response.data != null) {
			checkForErrors(response.errors)
			return response.data
		} else {
			logger.error("Oppslag mot personregisteret feilet. Fikk feil i kallet til personregisteret")
			throw BackendErrorException("Oppslag mot personregisteret feilet. Fikk feil i kallet for å hente person fra personregisteret")
		}
	}

	@Cacheable("getPrefillPersonInfo")
	override suspend fun getPrefillPersonInfo(ident: String): PrefillData.Result? {
		logger.info("Skal hente en preutfyllingsinfo fra PDL")

		// Does not include historic data
		val response = pdlGraphQLClient.execute(PrefillData(PrefillData.Variables(ident, false)))

		return if (response.data != null) {
			checkForErrors(response.errors)
			response.data
		} else {
			logger.error("Oppslag mot personregisteret for preutfylling feilet")
			null
		}
	}


	@Cacheable("hentIdenter")
	suspend fun hentIdenter(ident: String): HentIdenter.Result? {
		val response = pdlGraphQLClient.execute(
			HentIdenter(
				HentIdenter.Variables(ident)
			)
		)
		if (response.data != null) {
			checkForErrors(response.errors)
			logger.debug("Hentet identer")
			return response.data
		} else {
			logger.error("Oppslag mot personregisteret feilet. Fikk feil i kall for å hente identer fra personregisteret")
			throw BackendErrorException("Oppslag mot personregisteret feilet. Fikk feil i kallet for å hente identer fra personregisteret")
		}
	}

	private fun checkForErrors(errors: List<GraphQLClientError>?) {
		errors?.let { handleErrors(it, "Personregister") }
	}


}
