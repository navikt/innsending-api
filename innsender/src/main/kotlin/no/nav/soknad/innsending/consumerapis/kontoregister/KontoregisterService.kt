package no.nav.soknad.innsending.consumerapis.kontoregister

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.exceptions.NonCriticalException
import no.nav.soknad.innsending.kontoregisterborger.api.KontoregisterV1Api
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("test | dev | prod")
class KontoregisterService(
	restConfig: RestConfig,
	@Qualifier("kontoregisterApiClient")
	kontoregisterApiClient: OkHttpClient
) : KontoregisterInterface {
	private val kontoregisterApi = KontoregisterV1Api(restConfig.kontoregisterUrl, kontoregisterApiClient)

	private val logger: Logger = LoggerFactory.getLogger(javaClass)

	override fun getKontonummer(): String {
		try {
			return kontoregisterApi.kontooppslagMedGet().kontonummer
		} catch (e: Exception) {
			val errorMessage = "Kunne ikke hente kontonummer fra kontoregister"
			logger.error(errorMessage, e)
			throw NonCriticalException("Kunne ikke hente kontonummer fra kontoregister", e)
		}
	}

}
