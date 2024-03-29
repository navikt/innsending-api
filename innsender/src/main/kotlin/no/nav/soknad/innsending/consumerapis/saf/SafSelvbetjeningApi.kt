package no.nav.soknad.innsending.consumerapis.saf

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import com.expediagroup.graphql.client.types.GraphQLClientError
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.handleErrors
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.consumerapis.saf.dto.Dokument
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.safselvbetjening.generated.HentDokumentOversikt
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Journalposttype
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.DokumentInfo
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.Dokumentoversikt
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("test | dev | prod")
@Qualifier("saf")
class SafSelvbetjeningApi(
	private val safSelvbetjeningGraphQLClient: GraphQLWebClient
) : SafSelvbetjeningInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	// Følgende liste er generert på basis av ulike temaer på dokumentinnsending søknader funnet i henvendelsesbasen for 2021/2022
	private val relevanteTema = listOf(
		"AAP",
		"BAR",
		"BID",
		"BIL",
		"DAG",
		"ENF",
		"FOS",
		"GEN",
		"GRA",
		"HJE",
		"IND",
		"KON",
		"MED",
		"OMS",
		"OPP",
		"PEN",
		"SYK",
		"TSO",
		"TSR",
		"UFO",
		"VEN",
		"YRK"
	)

	override fun ping(): String {
//		healthApi.ping()
		return "pong"
	}

	override fun isReady(): String {
		// Ikke implementert kall mot SAF for å sjekke om tjenesten er oppe.
		return "ok"
	}

	override fun isAlive(): String {
//		healthApi.isReady()
		return "ok"
	}


	override fun hentBrukersSakerIArkivet(brukerId: String): List<ArkiverteSaker> {
		return runBlocking {
			try {
				val hentetDokumentoversikt = getSoknadsDataForPerson(brukerId)
				if (hentetDokumentoversikt == null || hentetDokumentoversikt.journalposter.isEmpty()) {
					throw BackendErrorException("Ingen søknader funnet. Fant ingen relevante søknader i søknadsarkivet")
				} else {
					val dokumentoversikt = filtrerPaJournalposttypeAndTema(
						hentetDokumentoversikt,
						listOf(Journalposttype.I), relevanteTema
					)
					dokumentoversikt.journalposter
						.map {
							ArkiverteSaker(
								it.eksternReferanseId, it.tittel ?: "", it.tema ?: "",
								it.relevanteDatoer[0]?.dato, konverterTilDokumentListe(it.dokumenter ?: emptyList())
							)
						}
				}
			} catch (ex: Exception) {
				logger.warn("hentBrukersSakerIArkivet feilet med ${ex.message}.")
				throw BackendErrorException("Henting av brukers innsendte søknader feilet", ex)
			}
		}
	}

	private fun konverterTilDokumentListe(dokumentInfo: List<DokumentInfo?>): List<Dokument> {
		if (dokumentInfo.isEmpty()) return emptyList()

		val hoveddokument = dokumentInfo.first()
		val vedlegg = dokumentInfo.drop(1)
		fun konverter(dokument: DokumentInfo?, type: String) =
			Dokument(KonverteringsUtility().brevKodeKontroll(dokument?.brevkode), dokument?.tittel ?: "", type)

		return listOf(konverter(hoveddokument, "Hoveddokument"))
			.plus(vedlegg.map { konverter(it, "Vedlegg") })
	}

	fun filtrerPaJournalposttypeAndTema(
		dokumentOversikt: Dokumentoversikt,
		journalposttyper: List<Journalposttype>, temaer: List<String>
	): Dokumentoversikt {
		return Dokumentoversikt(
			journalposter = dokumentOversikt.journalposter
				.filter { journalposttyper.contains(it.journalposttype) && temaer.contains(it.tema) }
		)
	}


	suspend fun getSoknadsDataForPerson(brukerId: String): Dokumentoversikt? {
		val response = safSelvbetjeningGraphQLClient.execute(
			HentDokumentOversikt(
				HentDokumentOversikt.Variables(brukerId)
			)
		)
		if (response.data != null) {
			checkForErrors(response.errors)
			return response.data?.dokumentoversiktSelvbetjening
		} else {
			logger.error("Oppslag mot søknadsarkivet feilet, ingen data returnert.")
			throw BackendErrorException("Oppslag mot søknadsarkivet feilet. Fikk feil i kallet til søknadsarkivet")
		}
	}

	private fun checkForErrors(errors: List<GraphQLClientError>?) {
		errors?.let { handleErrors(it, "søknadsarkiv") }
	}
}
