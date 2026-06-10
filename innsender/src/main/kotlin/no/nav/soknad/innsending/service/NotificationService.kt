package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.brukernotifikasjon.NotificationOptions
import no.nav.soknad.innsending.repository.SoknadRepository
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class NotificationService(
	private val publisher: BrukernotifikasjonPublisher,
	private val soknadRepository: SoknadRepository,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	@Async
	fun create(innsendingsId: String, opts: NotificationOptions = NotificationOptions()) {
		logger.debug("$innsendingsId: Skal opprette brukernotifikasjon")
		try {
			soknadRepository.findByInnsendingsid(innsendingsId)?.let {
				val result = publisher.createNotification(it, opts)
				logger.debug("$innsendingsId: status oppretting brukernotifikasjon: $result")
			} ?: logSoknadNotFound(innsendingsId)
		} catch (e: Exception) {
			logger.error("$innsendingsId: Noe gikk galt ved opprettelse av brukernotifikasjon", e)
		}
	}

	@Async
	fun close(innsendingsId: String) {
		logger.debug("$innsendingsId: Skal lukke brukernotifikasjon")
		try {
			soknadRepository.findByInnsendingsid(innsendingsId)?.let {
				val result = publisher.closeNotification(it)
				logger.debug("$innsendingsId: status lukking brukernotifikasjon: $result")
			} ?: logSoknadNotFound(innsendingsId)
		} catch (e: Exception) {
			logger.error("$innsendingsId: Noe gikk galt ved lukking av brukernotifikasjon", e)
		}
	}

	private fun logSoknadNotFound(innsendingsId: String) = logger.warn("$innsendingsId: Fant ikke søknad med gitt innsendingsid")

}
