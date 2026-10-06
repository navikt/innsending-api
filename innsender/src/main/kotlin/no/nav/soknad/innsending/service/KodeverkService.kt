package no.nav.soknad.innsending.service

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import no.nav.soknad.innsending.consumerapis.kodeverk.KodeverkType
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.kodeverk.api.KodeverkApi
import no.nav.soknad.innsending.kodeverk.model.GetKodeverkKoderBetydningerResponse
import no.nav.soknad.innsending.model.OpprettEttersending
import no.nav.soknad.innsending.util.finnBackupLanguage
import no.nav.soknad.innsending.util.finnSpraakFraInput
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Duration

@Service
class KodeverkService(
	@Qualifier("kodeverkApiClient") kodeverkApiClient: RestClient
) {

	val logger: Logger = LoggerFactory.getLogger(javaClass)
	private val kodeverkApi = KodeverkApi(kodeverkApiClient)

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
			logger.warn("Kunne ikke hente kodeverk: ${kodeverkType.value}", e)
			throw BackendErrorException(
				message = "Kunne ikke hente kodeverk: ${kodeverkType.value}",
				errorCode = ErrorCode.KODEVERK_ERROR,
				cause = e
			)
		}
		return response
	}

	fun getFormTitle(skjemanr: String, spraak: String): String? =
		getTitle(KodeverkType.KODEVERK_NAVSKJEMA, skjemanr, spraak)

	fun getAttachmentTitle(vedleggsnr: String, spraak: String): String? =
		getTitle(KodeverkType.KODEVERK_VEDLEGGSKODER, vedleggsnr, spraak)

	private fun getTitle(kodeverkType: KodeverkType, kode: String, spraak: String): String? {
		val beskrivelser = getKodeverk(kodeverkType)
			?.betydninger
			?.get(kode)
			?.firstOrNull()
			?.beskrivelser
			?: return null
		val language = finnSpraakFraInput(spraak)
		val backupLanguage = finnBackupLanguage(language).let { if (it == "no") "nb" else it }

		return beskrivelser[language]?.term
			?: beskrivelser[backupLanguage]?.term
			?: beskrivelser["nb"]?.term
	}

	// Fill missing form and attachment titles without replacing titles supplied by clients.
	fun enrichEttersendingWithKodeverkInfo(ettersending: OpprettEttersending): OpprettEttersending {
		val sprak = finnSpraakFraInput(ettersending.sprak)

		return ettersending.copy(
			tittel = ettersending.tittel
				?: getFormTitle(ettersending.skjemanr, sprak),
			vedleggsListe = ettersending.vedleggsListe?.map { vedlegg ->
				vedlegg.copy(
					tittel = vedlegg.tittel ?: getAttachmentTitle(vedlegg.vedleggsnr, sprak)
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

				else -> return
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

	fun getPoststed(postnr: String): String? {
		val kodeverk = getKodeverk(KodeverkType.KODEVERK_POSTNUMMER) ?: return null
		return kodeverk
			.betydninger[postnr]
			.let { it?.first()?.beskrivelser?.get("nb")?.term }
	}
}
