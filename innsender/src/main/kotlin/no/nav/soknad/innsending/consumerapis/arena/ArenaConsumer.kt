package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.api.MaalgrupperApi
import no.nav.soknad.innsending.api.TilleggsstonaderApi
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.AktivitetEndepunkt
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.security.SubjectHandlerInterface
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
	private val secureLogger = LoggerFactory.getLogger("secureLogger")

	private val fromDate = LocalDate.now().minusMonths(6)
	private val toDate = LocalDate.now().plusMonths(2)

	override fun getMaalgrupper(): List<Maalgruppe> {
		val userId = subjectHandler.getUserIdFromToken()

		logger.info("Henter målgrupper")
		secureLogger.info("[{}] Henter målgrupper", userId)

		val maalgrupper = try {
			maalgruppeApi.getMaalgrupper(fromDate.toString(), toDate.toString())
		} catch (e: RestClientResponseException) {
			if (e.statusCode.is4xxClientError) {
				logger.warn("Klientfeil ved henting av målgrupper", e)
				return emptyList()
			} else {
				logger.error("Serverfeil ved henting av målgrupper", e)
				return emptyList()
			}
		}

		secureLogger.info("[{}] Målgrupper: {}", userId, maalgrupper.map { it.maalgruppetype })

		return maalgrupper
	}

	override fun getAktiviteter(aktivitetEndepunkt: AktivitetEndepunkt): List<Aktivitet> {
		val userId = subjectHandler.getUserIdFromToken()

		logger.info("Henter aktiviteter")
		secureLogger.info("[{}] Henter aktiviteter", userId)

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
				logger.warn("Klientfeil ved henting av aktiviteter", e)
				return emptyList()
			} else {
				logger.error("Serverfeil ved henting av aktiviteter", e)
				return emptyList()
			}
		}

		secureLogger.info(
			"[{}] Aktiviteter: {}",
			userId,
			aktiviteter.map { "aktivitet="+it.aktivitetstype + ", er stønadsberettiget=" + it.erStoenadsberettigetAktivitet +"," +
				" tema=" + it.saksinformasjon?.sakstype +
				", periode="+it.periode.fom.toString() + "-" + it.periode.tom?.toString() +
				", parkering = " + it.saksinformasjon?.vedtaksinformasjon?.first()?.trengerParkering}
		)

		return aktiviteter
	}

}
