package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.brukernotifikasjon.NotificationOptions
import no.nav.soknad.innsending.repository.SoknadRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NotificationService(
	private val publisher: BrukernotifikasjonPublisher,
	private val soknadRepository: SoknadRepository,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun create(innsendingsId: String, opts: NotificationOptions = NotificationOptions()) {
		logger.debug("$innsendingsId: Skal opprette brukernotifikasjon")
		soknadRepository.findByInnsendingsid(innsendingsId)?.let {
			publisher.createNotification(it, opts)
		} ?: logSoknadNotFound(innsendingsId)
	}

	fun close(innsendingsId: String) {
		logger.debug("$innsendingsId: Skal lukke brukernotifikasjon")
		soknadRepository.findByInnsendingsid(innsendingsId)?.let {
			publisher.closeNotification(it)
		} ?: logSoknadNotFound(innsendingsId)
	}

	private fun logSoknadNotFound(innsendingsId: String) = logger.warn("$innsendingsId: Fant ikke søknad med gitt innsendingsid")

}
