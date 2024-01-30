package no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher

import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable

interface PublisherInterface {
	@Retryable(
		maxAttempts = 3,
		backoff = Backoff(delay = 500)
	)
	fun avsluttBrukernotifikasjon(soknadRef: SoknadRef)

	@Retryable(
		maxAttempts = 3,
		backoff = Backoff(delay = 500)
	)
	fun opprettBrukernotifikasjon(nyNotifikasjon: AddNotification)

}
