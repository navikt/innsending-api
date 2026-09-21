package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SkjemaService(
	private val hentSkjemaDataConsumer: HentSkjemaDataConsumer,
	private val kodeverkService: KodeverkService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun hentSkjema(nr: String, spraak: String, kastException: Boolean = true) = try {
		hentSkjemaDataConsumer.hentSkjemaEllerVedlegg(nr, spraak)
	} catch (ex: BackendErrorException) {
		val kodeverkTittel = kodeverkService.getAttachmentTitle(nr, spraak)
		if (kodeverkTittel != null) {
			KodeverkSkjema(skjemanummer = nr, tittel = kodeverkTittel)
		} else if (kastException) {
			throw ResourceNotFoundException(ex.message, ex)
		} else {
			logger.warn("Skjemanr=$nr ikke funnet i Sanity. Fortsetter behandling")
			KodeverkSkjema()
		}
	}
}
