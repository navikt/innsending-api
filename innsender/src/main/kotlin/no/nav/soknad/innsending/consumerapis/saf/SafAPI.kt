package no.nav.soknad.innsending.consumerapis.saf

import com.expediagroup.graphql.client.spring.GraphQLWebClient
import com.expediagroup.graphql.client.types.GraphQLClientError
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.saf.dto.ArkiverteSaker
import no.nav.soknad.innsending.consumerapis.saf.dto.Dokument
import no.nav.soknad.innsending.exceptions.SafApiException
import no.nav.soknad.innsending.safselvbetjening.generated.HentDokumentOversikt
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Journalposttype
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.DokumentInfo
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.Dokumentoversikt
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Utilities
import no.nav.soknad.innsending.util.testpersonid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Profile("test | dev | prod")
@Qualifier("saf")
class SafAPI(
	private val safSelvbetjeningGraphQLClient: GraphQLWebClient,
	private val tokenUtil: SubjectHandlerInterface
): SafInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val relevanteTema = listOf("AAP","BAR","BID","BIL","DAG","ENF","FOS","GEN","GRA","HJE","IND","KON","MED","OMS","OPP","PEN","SYK","TSO","TSR","UFO","VEN","YRK")

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


	override fun hentBrukersSakerIArkivet(brukerId:String): List<ArkiverteSaker>? {
		//val subject = tokenUtil.getUserIdFromToken()
		return runBlocking	{
			try {
				val hentetDokumentoversikt = getSoknadsDataForPerson(brukerId)
				if (hentetDokumentoversikt == null || hentetDokumentoversikt.journalposter.isEmpty()) {
					throw SafApiException("Ingen søknader funnet", "Fant ingen relevante søknader i søknadsarkivet")
				} else {
					val dokumentoversikt = filtrerPaJournalposttypeAndTema(
						hentetDokumentoversikt,
						listOf(Journalposttype.I), relevanteTema
					)
					dokumentoversikt.journalposter
						.map {
							ArkiverteSaker(
								it.eksternReferanseId, it.tittel ?: "", it.tema ?: "",
								it.relevanteDatoer.get(0)?.dato?.toString(), konverterTilDokumentListe(it.dokumenter)
							)
						}
						.toList()
				}
			} catch (ex: Exception) {
				logger.warn("hentBrukersSakerIArkivet feilet med ${ex.message}. Bruker midlertidig dummy data for SAF")
				dummyArkiverteSoknader[brukerId] // TODO fjern
			}
		}
	}

	private fun konverterTilDokumentListe(dokumentInfo: List<DokumentInfo?>?): List<Dokument> {
		val dokumenter = mutableListOf<Dokument>()
		if (dokumentInfo == null || dokumentInfo.isEmpty()) return dokumenter

		dokumenter.add(Dokument(dokumentInfo.get(0)?.brevkode, dokumentInfo.get(0)?.tittel ?: "", "Hoveddokument" ))

		if (dokumentInfo.size>1) {
			dokumentInfo.subList(1, dokumentInfo.size - 1).forEach { dokumenter.add (Dokument(it?.brevkode ?: "", it?.tittel ?: "", "Vedlegg")) }
		}
		return dokumenter
	}
	fun filtrerPaJournalposttypeAndTema(dokumentOversikt: Dokumentoversikt,
																			journalposttyper: List<Journalposttype>, temaer: List<String>): Dokumentoversikt {
		return Dokumentoversikt(
			journalposter = dokumentOversikt.journalposter
				.filter { journalposttyper.contains(it.journalposttype) && temaer.contains(it.tema) }
		)
	}


	suspend fun getSoknadsDataForPerson(brukerId: String): Dokumentoversikt? {
			val response = safSelvbetjeningGraphQLClient.execute (
				HentDokumentOversikt(
					HentDokumentOversikt.Variables(brukerId)
				)
			)
			if (response.data != null) {
				checkForErrors(response.errors)
				return response.data?.dokumentoversiktSelvbetjening
			} else {
				logger.error("Oppslag mot søknadsarkivet feilet, ingen data returnert.")
				throw SafApiException("Oppslag mot søknadsarkivet feilet", "Fikk feil i kallet til søknadsarkivet")
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


	// ***** Midlertidig dummy data -> **********

	private val date = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)

	private val dummyArkiverteSoknader = mapOf (
		testpersonid to listOf (
			ArkiverteSaker(
				Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			)),
			ArkiverteSaker(
				Utilities.laginnsendingsId(), "Ettersending til test søknad", "BID", date
				, listOf(
					Dokument("NAVe 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
					Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
				))
		),
		"12345678901" to listOf(ArkiverteSaker(
			Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			))),
		"12345678902" to listOf(ArkiverteSaker(
			Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			))),
		"12345678903" to listOf(ArkiverteSaker(
			Utilities.laginnsendingsId(), "Test søknad", "BID", date
			, listOf(
				Dokument("NAV 08-09.06","Egenerklæring og sykmelding", "HOVEDDOKUMENT"),
				Dokument("N6","Et vedleggEgenerklæring og sykmelding", "VEDLEGG")
			)))
	)
}
