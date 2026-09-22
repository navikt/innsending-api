package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service

@Service
class SkjemaService(private val kodeverkService: KodeverkService) {

	fun hentSkjema(nr: String, spraak: String): KodeverkSkjema =
		kodeverkService.getFormTitle(nr, spraak)
			?.let { KodeverkSkjema(skjemanummer = nr, tittel = it) }
			?: throw ResourceNotFoundException("Skjema med id = $nr ikke funnet i kodeverk")

	fun hentVedlegg(nr: String, spraak: String): KodeverkSkjema =
		kodeverkService.getAttachmentTitle(nr, spraak)
			?.let { KodeverkSkjema(skjemanummer = nr, tittel = it) }
			?: throw ResourceNotFoundException("Vedlegg med id = $nr ikke funnet i kodeverk")
}
