package no.nav.soknad.innsending.consumerapis.saf

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.handleErrors
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.saf.generated.HentDokumentoversiktBruker
import no.nav.soknad.innsending.saf.generated.hentdokumentoversiktbruker.Dokumentoversikt
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("test | dev | prod")
@Qualifier("safClient")
class SafClient(
	private val safGraphQLWebClient: GraphQLWebClient
) {
	private val logger = LoggerFactory.getLogger(javaClass)

fun hentDokumentoversiktBruker(brukerId: String): List<ArkiverteSaker> {
	return runBlocking {
		try {
			val dokumentoversikt = execute(brukerId)
			dokumentoversikt.journalposter.filterNotNull().map {
				ArkiverteSaker(
					it.eksternReferanseId, it.tittel ?: "", it.tema.toString(),
					it.datoOpprettet, emptyList()
				)
			}
		} catch (ex: Exception) {
			logger.warn("hentDokumentoversiktBruker feilet med ${ex.message}.")
			throw BackendErrorException(ex.message, "Henting av brukers innsendte søknader feilet", "errorCode.backendError.safError")
		}
	}
}


	suspend fun execute(brukerId: String): Dokumentoversikt {
		val response = safGraphQLWebClient.execute(
			HentDokumentoversiktBruker(
				HentDokumentoversiktBruker.Variables(brukerId)
			)
		)
		if (!response.errors.isNullOrEmpty()) {
			handleErrors(response.errors!!, "saf")
		}
		if (response.data != null) {
			return response.data!!.dokumentoversiktBruker
		}
		throw RuntimeException("Oppslag mot saf feilet, ingen data returnert.")
	}

}
