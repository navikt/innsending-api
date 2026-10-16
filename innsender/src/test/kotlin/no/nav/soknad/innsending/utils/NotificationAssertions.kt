package no.nav.soknad.innsending.utils

import io.mockk.verify
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface

fun PublisherInterface.awaitNotification(submissionId: String): AddNotification {
	verify(timeout = 5000, exactly = 1) {
		opprettBrukernotifikasjon(match { it.soknadRef.innsendingId == submissionId })
	}
	val notifications = mutableListOf<AddNotification>()
	verify(atLeast = 1) {
		opprettBrukernotifikasjon(capture(notifications))
	}
	return notifications.single { it.soknadRef.innsendingId == submissionId }
}
