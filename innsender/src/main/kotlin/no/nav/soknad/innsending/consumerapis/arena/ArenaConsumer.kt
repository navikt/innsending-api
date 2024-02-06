package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.api.MaalgrupperApi
import no.nav.soknad.innsending.api.TilleggsstonaderApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import okhttp3.OkHttpClient
import org.openapitools.client.infrastructure.ClientException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@Profile("test | dev | prod")
class ArenaConsumer(
	@Qualifier("arenaApiClient")
	private val arenaApiClient: OkHttpClient,
	private val subjectHandler: SubjectHandlerInterface,
	restConfig: RestConfig,
) : ArenaConsumerInterface {

	private val maalgruppeApi = MaalgrupperApi(restConfig.arenaUrl, arenaApiClient)
	private val tilleggsstonaderApi = TilleggsstonaderApi(restConfig.arenaUrl, arenaApiClient)

	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")

	private val fromDate = LocalDate.now().minusMonths(6)
	private val toDate = LocalDate.now().plusMonths(2)

	override fun getMaalgrupper(): List<Maalgruppe> {
		val userId = subjectHandler.getUserIdFromToken()

		logger.info("baseurl ${maalgruppeApi.baseUrl}")
		logger.info("fom tom $fromDate $toDate")

		logger.info("Henter målgrupper")
		secureLogger.info("[{}] Henter målgrupper", userId)

		val maalgrupper = try {
			maalgruppeApi.getMaalgrupper(fromDate.toString(), toDate.toString())
		} catch (ex: ClientException) {
			logger.warn("Klientfeil ved henting av målgrupper", ex)
			return emptyList()
		} catch (ex: Exception) {
			logger.error("Serverfeil ved henting av målgrupper", ex)
			return emptyList()
		}

		secureLogger.info("[{}] Målgrupper: {}", userId, maalgrupper.map { it.maalgruppetype })

		return maalgrupper
	}

	override fun getAktiviteter(): List<Aktivitet> {
		val userId = subjectHandler.getUserIdFromToken()

		logger.info("Henter aktiviteter")
		secureLogger.info("[{}] Henter aktiviteter", userId)

		val aktiviteter = try {
			tilleggsstonaderApi.getAktiviteter(fromDate.toString(), toDate.toString())
		} catch (ex: ClientException) {
			logger.warn("Klientfeil ved henting av aktiviteter", ex)
			return emptyList()
		} catch (ex: Exception) {
			logger.error("Serverfeil ved henting av aktiviteter", ex)
			return emptyList()
		}

		secureLogger.info("[{}] Aktiviteter: {}", userId, aktiviteter.map { it.aktivitetstype })

		return aktiviteter
	}

}
