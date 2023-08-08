package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.KodeverkSkjema
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.exceptions.SanityException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SkjemaService(
	private val hentSkjemaDataConsumer: HentSkjemaDataConsumer
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun hentSkjema(nr: String, spraak: String, kastException: Boolean = true) = try {
		hentSkjemaDataConsumer.hentSkjemaEllerVedlegg(nr, spraak)
	} catch (re: SanityException) {
		if (kastException) {
			throw ResourceNotFoundException(re.arsak, re.message ?: "", "errorCode.resourceNotFound.schemaNotFound")
		} else {
			logger.warn("Skjemanr=$nr ikke funnet i Sanity. Fortsetter behandling")
			KodeverkSkjema()
		}
	}
}
