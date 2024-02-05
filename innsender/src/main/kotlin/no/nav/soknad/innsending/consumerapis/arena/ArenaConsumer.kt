package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.api.MaalgrupperApi
import no.nav.soknad.innsending.api.TilleggsstonaderApi
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.MDCUtil
import okhttp3.OkHttpClient
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

	private val maalgruppeApi = MaalgrupperApi("${restConfig.arenaUrl}/api/v1/maalgrupper", arenaApiClient)
	private val tilleggsstonaderApi =
		TilleggsstonaderApi("${restConfig.arenaUrl}/v1/tilleggsstoenad/dagligreise", arenaApiClient)

	private val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val secureLogger = LoggerFactory.getLogger("secureLogger")

	private val fromDate = LocalDate.now().minusMonths(6)
	private val toDate = LocalDate.now().plusMonths(2)

	override fun getMaalgrupper(): List<Maalgruppe> {
		val callId = MDCUtil.callIdOrNew()
		val userId = subjectHandler.getUserIdFromToken()

		logger.info("Henter målgruppe med callId:{}", callId)
		secureLogger.info("[{}] Henter målgrupper for callId:{}", userId, callId)

		val maalgrupper = maalgruppeApi.getMaalgrupper(fromDate.toString(), toDate.toString())

		secureLogger.info("[{}] Målgrupper: {}", userId, maalgrupper.toString())

		return maalgrupper
	}

	override fun getAktiviteter(): List<Aktivitet> {
		val callId = MDCUtil.callIdOrNew()
		val userId = subjectHandler.getUserIdFromToken()

		logger.info("Henter aktiviteter for callId:{}", callId)
		secureLogger.info("[{}] Henter aktiviteter for callId:{}", userId, callId)

		var aktiviteter: List<Aktivitet> = emptyList()
		try {
			aktiviteter = tilleggsstonaderApi.getAktiviteter(fromDate.toString(), toDate.toString())
		} catch (ex: Exception) {
			logger.error("Feil ved henting av aktiviteter", ex)
		}

		secureLogger.info("[{}] Aktiviteter: {}", userId, aktiviteter.toString())

		return aktiviteter
	}

}
