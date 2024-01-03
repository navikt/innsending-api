package no.nav.soknad.innsending.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.kodeverk.KodeverkType
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.kodeverk.api.KodeverkApi
import no.nav.soknad.innsending.kodeverk.model.GetKodeverkKoderBetydningerResponse
import no.nav.soknad.innsending.model.OpprettEttersending
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class KodeverkService(
	restConfig: RestConfig,
	kodeverkApiClient: OkHttpClient
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val kodeverkApi = KodeverkApi(restConfig.kodeverkUrl, kodeverkApiClient)

	val cache: LoadingCache<String, GetKodeverkKoderBetydningerResponse> = Caffeine
		.newBuilder()
		.refreshAfterWrite(Duration.ofHours(1))
		.build {
			kodeverkApi.betydning(
				kodeverksnavn = it,
				spraak = setOf("nb", "nn", "en"),
				ekskluderUgyldige = true
			)
		}

	// Validate ettersending against the felles kodeverk
	fun validateEttersending(ettersending: OpprettEttersending, kodeverkTypes: List<KodeverkType>) {
		if (kodeverkTypes.isEmpty()) return

		kodeverkTypes.forEach { kodeverkType ->
			when (kodeverkType) {
				KodeverkType.KODEVERK_NAVSKJEMA -> validateValueInKodeverk(ettersending.skjemanr, kodeverkType)
				KodeverkType.KODEVERK_TEMA -> validateValueInKodeverk(ettersending.tema, kodeverkType)
				KodeverkType.KODEVERK_VEDLEGGSKODER -> validateValuesInKodeverk(
					ettersending.vedleggsListe?.map { it.vedleggsnr },
					kodeverkType
				)
			}
		}
	}

	private fun validateValueInKodeverk(value: String, kodeverkType: KodeverkType) {

		val response = try {
			cache.get(kodeverkType.value)
		} catch (e: Exception) {
			// Log error, but continue execution
			logger.error("Kodeverk error", e)
			return
		}

		if (response.betydninger[value] == null) {
			throw IllegalActionException(
				message = "$value finnes ikke i kodeverket: ${kodeverkType.value}",
				errorCode = ErrorCode.INVALID_KODEVERK_VALUE
			)
		}
	}

	private fun validateValuesInKodeverk(values: List<String>?, kodeverkType: KodeverkType) {
		if (values.isNullOrEmpty()) return

		val response = try {
			cache.get(kodeverkType.value)
		} catch (e: Exception) {
			// Log error, but continue execution
			logger.error("Kodeverk error", e)
			return
		}

		values.forEach { value ->
			if (response.betydninger[value] == null) {
				throw IllegalActionException(
					message = "$value finnes ikke i kodeverket: ${kodeverkType.value}",
					errorCode = ErrorCode.INVALID_KODEVERK_VALUE
				)
			}
		}
	}
}
