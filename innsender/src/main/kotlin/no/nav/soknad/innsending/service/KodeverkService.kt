package no.nav.soknad.innsending.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.kodeverk.KodeverkType
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.kodeverk.api.KodeverkApi
import no.nav.soknad.innsending.kodeverk.model.GetKodeverkKoderBetydningerResponse
import no.nav.soknad.innsending.model.OpprettEttersending
import no.nav.soknad.innsending.util.finnSpraakFraInput
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration

@Service
class KodeverkService(
	kodeverkApiClient: RestClient
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val kodeverkApi = KodeverkApi( kodeverkApiClient)

	val cache: LoadingCache<String, GetKodeverkKoderBetydningerResponse> = Caffeine
		.newBuilder()
		.refreshAfterWrite(Duration.ofHours(1))
		.build {
			kodeverkApi.betydning(
				kodeverksnavn = it,
				spraak = setOf("nb", "nn", "en"),
				ekskluderUgyldige = true,
				oppslagsdato = null,
				navConsumerId = null,
				navCallId = null
			)
		}

	private fun getKodeverk(kodeverkType: KodeverkType): GetKodeverkKoderBetydningerResponse? {
		val response = try {
			cache.get(kodeverkType.value)
		} catch (e: Exception) {
			throw BackendErrorException(
				message = "Kunne ikke hente kodeverk: ${kodeverkType.value}",
				errorCode = ErrorCode.KODEVERK_ERROR,
				cause = e
			)
		}
		return response
	}

	// Add extra info from kodeverk such as 'tittel' (if not specified in input)
	fun enrichEttersendingWithKodeverkInfo(ettersending: OpprettEttersending): OpprettEttersending {
		val sprak = finnSpraakFraInput(ettersending.sprak)

		val kodeverkNavSkjema = getKodeverk(KodeverkType.KODEVERK_NAVSKJEMA) ?: return ettersending
		val kodeverkVedleggskoder = getKodeverk(KodeverkType.KODEVERK_VEDLEGGSKODER) ?: return ettersending

		return ettersending.copy(
			tittel = ettersending.tittel
				?: kodeverkNavSkjema.betydninger[ettersending.skjemanr]?.first()?.beskrivelser?.get(sprak)?.term,
			vedleggsListe = ettersending.vedleggsListe?.map { vedlegg ->
				vedlegg.copy(
					tittel = vedlegg.tittel
						?: kodeverkVedleggskoder.betydninger[vedlegg.vedleggsnr]?.first()?.beskrivelser?.get(sprak)?.term
				)
			}
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
		val kodeverk = getKodeverk(kodeverkType) ?: return

		if (kodeverk.betydninger[value] == null) {
			throw IllegalActionException(
				message = "$value finnes ikke i kodeverket: ${kodeverkType.value}",
				errorCode = ErrorCode.INVALID_KODEVERK_VALUE
			)
		}
	}

	private fun validateValuesInKodeverk(values: List<String>?, kodeverkType: KodeverkType) {
		if (values.isNullOrEmpty()) return

		val kodeverk = getKodeverk(kodeverkType) ?: return

		values.forEach { value ->
			if (kodeverk.betydninger[value] == null) {
				throw IllegalActionException(
					message = "$value finnes ikke i kodeverket: ${kodeverkType.value}",
					errorCode = ErrorCode.INVALID_KODEVERK_VALUE
				)
			}
		}
	}
}
