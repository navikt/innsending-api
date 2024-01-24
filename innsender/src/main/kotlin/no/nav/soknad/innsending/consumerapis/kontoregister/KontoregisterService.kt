package no.nav.soknad.innsending.consumerapis.kontoregister

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.NonCriticalException
import no.nav.soknad.innsending.kontoregisterborger.api.KontoregisterV1Api
import no.nav.soknad.innsending.kontoregisterborger.infrastructure.ClientException
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
@Profile("test | dev | prod")
class KontoregisterService(
	restConfig: RestConfig,
	@Qualifier("kontoregisterApiClient")
	kontoregisterApiClient: OkHttpClient
) : KontoregisterInterface {
	private val kontoregisterApi = KontoregisterV1Api("${restConfig.kontoregisterUrl}/api/borger", kontoregisterApiClient)

	private val logger: Logger = LoggerFactory.getLogger(javaClass)

	override fun getKontonummer(): String {
		try {
			return kontoregisterApi.kontooppslagMedGet().kontonummer
		} catch (e: ClientException) {
			val errorMessage: String

			if (e.statusCode == HttpStatus.NOT_FOUND.value()) {
				errorMessage = "Fant ikke kontonummer i kontoregister"
				logger.warn(errorMessage, e)
			} else {
				errorMessage = "Klientfeil ved henting av kontonummer fra kontoregister"
				logger.warn(errorMessage, e)
			}

			throw NonCriticalException(errorMessage, e)
		} catch (e: Exception) {
			val errorMessage = "Feil ved henting av kontonummer fra kontoregister"

			logger.error(errorMessage, e)

			throw NonCriticalException(errorMessage, e)

		}
	}

}
