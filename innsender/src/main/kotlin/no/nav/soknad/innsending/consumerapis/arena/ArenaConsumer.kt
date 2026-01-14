package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.api.MaalgrupperApi
import no.nav.soknad.innsending.api.TilleggsstonaderApi
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.AktivitetEndepunkt
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.logging.CombinedLogger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDate

@Component
@Profile("test | dev | prod")
class ArenaConsumer(
	@Qualifier("arenaApiRestClient") private val arenaApiClient: RestClient,
	private val subjectHandler: SubjectHandlerInterface
) : ArenaConsumerInterface {

	private val maalgruppeApi = MaalgrupperApi(arenaApiClient)
	private val tilleggsstonaderApi = TilleggsstonaderApi(arenaApiClient)

	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val combinedLogger = CombinedLogger(logger)

	private val fromDate = LocalDate.now().minusMonths(6)
	private val toDate = LocalDate.now().plusMonths(2)

	override fun getMaalgrupper(): List<Maalgruppe> {
		val userId = subjectHandler.getUserIdFromToken()

		combinedLogger.log("Henter målgrupper", userId)

		val maalgrupper = try {
			maalgruppeApi.getMaalgrupper(fromDate.toString(), toDate.toString())
		} catch (e: RestClientResponseException) {
			if (e.statusCode.is4xxClientError) {
				logger.warn("[Arena] Klientfeil ved henting av målgrupper", e)
				return emptyList()
			} else {
				logger.warn("[Arena] Serverfeil ved henting av målgrupper", e)
				return emptyList()
			}
		}

		combinedLogger.log("Målgrupper hentet: ${maalgrupper.map { it.maalgruppetype } }", userId)

		return maalgrupper
	}

	override fun getAktiviteter(aktivitetEndepunkt: AktivitetEndepunkt): List<Aktivitet> {
		val userId = subjectHandler.getUserIdFromToken()

		combinedLogger.log("Henter aktiviteter", userId)

		val aktiviteter = try {
			when (aktivitetEndepunkt) {
				AktivitetEndepunkt.aktivitet -> tilleggsstonaderApi.getAktiviteter(fromDate.toString(), toDate.toString())
				AktivitetEndepunkt.dagligreise -> tilleggsstonaderApi.getAktiviteterDagligreise(
					fromDate.toString(),
					toDate.toString()
				)

				else -> throw BackendErrorException("Ukjent aktivitetstype")
			}
		} catch (e: RestClientResponseException) {
			if (e.statusCode.is4xxClientError) {
				logger.warn("[Arena] Klientfeil ved henting av aktiviteter", e)
				return emptyList()
			} else {
				logger.warn("[Arena] Serverfeil ved henting av aktiviteter", e)
				return emptyList()
			}
		}

		val logMelding = "Aktiviteter hentet: ${aktiviteter.map {
			"aktivitet=" + it.aktivitetstype + ", er stønadsberettiget=" + it.erStoenadsberettigetAktivitet + "," +
				" tema=" + it.saksinformasjon?.sakstype +
				", periode=" + it.periode.fom.toString() + "-" + it.periode.tom?.toString() +
				", parkering = " + it.saksinformasjon?.vedtaksinformasjon?.first()?.trengerParkering
		}}"
		combinedLogger.log(logMelding, userId)

		return aktiviteter
	}

}
