package no.nav.soknad.innsending.consumerapis.saf

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.handleErrors
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.saf.generated.HentDokumentoversiktBruker
import no.nav.soknad.innsending.saf.generated.enums.BrukerIdType
import no.nav.soknad.innsending.saf.generated.enums.Journalposttype
import no.nav.soknad.innsending.saf.generated.hentdokumentoversiktbruker.Dokumentoversikt
import no.nav.soknad.innsending.saf.generated.inputs.BrukerIdInput
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*


@Component
@Profile("test | dev | prod")
class SafClient(
	private val safGraphQLWebClient: GraphQLWebClient
) : SafClientInterface {
	private val logger = LoggerFactory.getLogger(javaClass)

	/**
	 * Denne funksjonen er ment for bruk kun internt i innsending-api
	 * pga. at tjenesten har ikke tilgangsstyring på brukernivå.
	 * Dataene som hentes skal altså ikke sendes ut til bruker.
	 * MERK at denne fuksjonen pt. ikke er i bruk da vi ikke kunne basere oss på oppslag mot arkivet med innsendt søknad sin brukerId,
	 * da det er tilfeller der saksbehandler endrer i arkivet id til innsender. Vi beholder koden i tilfelle vi i framtiden trenger den.
	 */
	override fun hentDokumentoversiktBruker(brukerId: String): List<ArkiverteSaker> {
		return runBlocking {
			try {
				val dokumentoversikt = execute(brukerId)
				dokumentoversikt.journalposter.filterNotNull().map {
					ArkiverteSaker(
						it.eksternReferanseId, "", "",
						it.datoOpprettet, emptyList()
					)
				}
			} catch (ex: Exception) {
				logger.warn("hentDokumentoversiktBruker feilet med ${ex.message}.")
				throw BackendErrorException(ex.message, "Henting av brukers dokumentoversikt feilet", "errorCode.backendError.safError")
			}
		}
	}

	suspend fun execute(brukerId: String): Dokumentoversikt {
		val response = safGraphQLWebClient.execute(
			HentDokumentoversiktBruker(
				HentDokumentoversiktBruker.Variables(
					BrukerIdInput(id = brukerId, type = BrukerIdType.FNR),
					10, listOf(Journalposttype.I), listOf(), formatDate(LocalDateTime.now().minusDays(2))
				)
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

	fun formatDate(date: LocalDateTime): String {
		val formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd")
		return date.format(formatter)
	}


	fun format(date: LocalDateTime): String {
		val formatter = DateTimeFormatter.ofPattern("YYYY-MM-DD'T'hh:mm:ss")
		return date.format(formatter)
	}

}
