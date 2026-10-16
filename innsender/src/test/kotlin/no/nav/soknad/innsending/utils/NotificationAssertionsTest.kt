package no.nav.soknad.innsending.utils

import io.mockk.mockk
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class NotificationAssertionsTest {
	@Test
	fun `selects the current submission after an unrelated callback`() {
		val publisher = mockk<PublisherInterface>(relaxUnitFun = true)
		val previous = AddNotification(
			soknadRef = SoknadRef(
				innsendingId = "previous",
				erEttersendelse = false,
				groupId = "group",
				personId = "test-user",
				tidpunktEndret = OffsetDateTime.parse("2026-01-01T00:00:00Z")
			),
			brukernotifikasjonInfo = mockk()
		)
		val current = previous.copy(soknadRef = previous.soknadRef.copy(innsendingId = "current"))
		publisher.opprettBrukernotifikasjon(previous)
		publisher.opprettBrukernotifikasjon(current)

		assertSame(current, publisher.awaitNotification("current"))
	}
}
