package no.nav.soknad.innsending.consumerapis.kontoregister

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.kontoregisterborger.api.KontoregisterV1Api
import okhttp3.OkHttpClient
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("dev | prod")
class KontoregisterService(
	restConfig: RestConfig,
	kontoregisterApiClient: OkHttpClient
) : KontoregisterInterface {
	private val kontoregisterApi = KontoregisterV1Api(restConfig.kontoregisterUrl, kontoregisterApiClient)

	override fun getKontonummer(): String {
		return kontoregisterApi.kontooppslagMedGet().kontonummer
	}

}
