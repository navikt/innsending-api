package no.nav.soknad.innsending.brukernotifikasjon.kafka

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SendTilPublisherRetryTest : ApplicationTest() {

	@SpykBean
	private lateinit var sendTilPublisher: PublisherInterface

	@Autowired
	lateinit var brukernotifikasjonPublisher: BrukernotifikasjonPublisher

	@BeforeEach
	fun setUp() = clearAllMocks()

	@Test
	fun `attempts to send notification three times before giving up`() {
		val innsendingsid = "123456"

		every { sendTilPublisher.opprettBrukernotifikasjon(any()) } throws RuntimeException("Feil ved sending av brukernotifikasjon")

		val soknad = SoknadDbDataTestBuilder(innsendingsId = innsendingsid).build()
		val notificationCreated = brukernotifikasjonPublisher.createNotification(soknad)

		val notifications = mutableListOf<AddNotification>()
		verify(exactly = 3) { sendTilPublisher.opprettBrukernotifikasjon(capture(notifications)) }

		assertFalse { notificationCreated }
		assertEquals(3, notifications.size)
		assertTrue { notifications.all { it.soknadRef.innsendingId == soknad.innsendingsid } }
	}

	@Test
	fun `sends the notification the second time after one failure`() {
		val innsendingsid = "123456"

		every { sendTilPublisher.opprettBrukernotifikasjon(any()) }
			.throws(RuntimeException("First failure"))
			.andThen(Unit)

		val soknad = SoknadDbDataTestBuilder(innsendingsId = innsendingsid).build()
		val notificationCreated = brukernotifikasjonPublisher.createNotification(soknad)

		val notifications = mutableListOf<AddNotification>()
		verify(exactly = 2) { sendTilPublisher.opprettBrukernotifikasjon(capture(notifications)) }

		assertTrue { notificationCreated }
		assertEquals(2, notifications.size)
		assertTrue { notifications.all { it.soknadRef.innsendingId == soknad.innsendingsid } }
	}

	@Test
	fun `sends the notification the third time after two failures`() {
		val innsendingsid = "123456"

		every { sendTilPublisher.opprettBrukernotifikasjon(any()) }
			.throws(RuntimeException("First failure"))
			.andThenThrows(RuntimeException("Second failure"))
			.andThen(Unit)

		val soknad = SoknadDbDataTestBuilder(innsendingsId = innsendingsid).build()
		val notificationCreated = brukernotifikasjonPublisher.createNotification(soknad)

		val notifications = mutableListOf<AddNotification>()
		verify(exactly = 3) { sendTilPublisher.opprettBrukernotifikasjon(capture(notifications)) }

		assertTrue { notificationCreated }
		assertEquals(3, notifications.size)
		assertTrue { notifications.all { it.soknadRef.innsendingId == soknad.innsendingsid } }
	}

}
