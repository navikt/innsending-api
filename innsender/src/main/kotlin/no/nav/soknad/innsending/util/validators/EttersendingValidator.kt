package no.nav.soknad.innsending.util.validators

import no.nav.soknad.innsending.consumerapis.kodeverk.KodeverkType
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.kodeverk.api.KodeverkApi
import no.nav.soknad.innsending.kodeverk.model.GetKodeverkKoderResponse
import no.nav.soknad.innsending.model.OpprettEttersending
import no.nav.soknad.innsending.util.Constants.APPLICATION_NAME
import no.nav.soknad.innsending.util.MDCUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EttersendingValidator {

	val logger: Logger = LoggerFactory.getLogger(javaClass)

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
		val response: GetKodeverkKoderResponse

		try {
			val kodeverkApi = KodeverkApi()
			response = kodeverkApi.koder(kodeverkType.value, APPLICATION_NAME, MDCUtil.callIdOrNew())
		} catch (e: Exception) {
			// Log error, but continue execution
			logger.error("Kodeverk error", e)
			return
		}

		if (!response.koder.contains(value)) {
			throw IllegalActionException(
				message = "$value finnes ikke i kodeverket: ${kodeverkType.value}",
				errorCode = ErrorCode.INVALID_KODEVERK_VALUE
			)
		}
	}

	private fun validateValueInKodeverk(values: List<String>?, kodeverkType: KodeverkType) {
		if (values.isNullOrEmpty()) return

		val response: GetKodeverkKoderResponse

		try {
			val kodeverkApi = KodeverkApi()
			response = kodeverkApi.koder(kodeverkType.value, APPLICATION_NAME, MDCUtil.callIdOrNew())
		} catch (e: Exception) {
			// Log error, but continue execution
			logger.error("Kodeverk error", e)
			return
		}

		values.forEach { value ->
			if (!response.koder.contains(value)) {
				throw IllegalActionException(
					message = "$value finnes ikke i kodeverket: ${kodeverkType.value}",
					errorCode = ErrorCode.INVALID_KODEVERK_VALUE
				)
			}
		}
	}
}
