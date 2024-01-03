package no.nav.soknad.innsending.util.validators

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
import org.springframework.stereotype.Component

@Component
class EttersendingValidator(
	private val restConfig: RestConfig,
	private val kodeverkApiClient: OkHttpClient
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val kodeverkApi = KodeverkApi(restConfig.kodeverkUrl, kodeverkApiClient)

	// Validate ettersending against the felles kodeverk
	fun validateEttersending(ettersending: OpprettEttersending, kodeverkTypes: List<KodeverkType>) {
		if (kodeverkTypes.isEmpty()) return

		kodeverkTypes.forEach { kodeverkType ->
			when (kodeverkType) {
				KodeverkType.KODEVERK_NAVSKJEMA -> validateValueInKodeverk(ettersending.skjemanr, kodeverkType)
				KodeverkType.KODEVERK_TEMA -> validateValueInKodeverk(ettersending.tema, kodeverkType)
				KodeverkType.KODEVERK_VEDLEGGSKODER -> validateValueInKodeverk(
					ettersending.vedleggsListe?.map { it.vedleggsnr },
					kodeverkType
				)
			}
		}
	}

	private fun validateValueInKodeverk(value: String, kodeverkType: KodeverkType) {
		val response: GetKodeverkKoderBetydningerResponse

		try {
			response = kodeverkApi.betydning(
				kodeverksnavn = kodeverkType.value,
				spraak = setOf("nb", "nn", "en"),
				ekskluderUgyldige = true
			)
		} catch (e: Exception) {
			// Log error, but continue execution
			println(e)
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

	private fun validateValueInKodeverk(values: List<String>?, kodeverkType: KodeverkType) {
		if (values.isNullOrEmpty()) return

		val response: GetKodeverkKoderBetydningerResponse

		try {
			response = kodeverkApi.betydning(
				kodeverksnavn = kodeverkType.value,
				spraak = setOf("nb", "nn", "en"),
				ekskluderUgyldige = true
			)
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
