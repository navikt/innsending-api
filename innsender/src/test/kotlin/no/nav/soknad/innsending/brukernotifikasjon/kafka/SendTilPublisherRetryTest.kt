package no.nav.soknad.innsending.brukernotifikasjon.kafka

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertTrue

class SendTilPublisherRetryTest : ApplicationTest() {

	@Autowired
	lateinit var soknadRepository: SoknadRepository

	@SpykBean
	private lateinit var sendTilPublisher: PublisherInterface

	@SpykBean
	private lateinit var brukernotifikasjonPublisher: BrukernotifikasjonPublisher

	@SpykBean
	private lateinit var notificationService: NotificationService

	@BeforeEach
	fun setUp() = clearAllMocks()

	@Test
	fun `attempts to send notification three times before giving up`() {
		// Given
		val innsendingsid = UUID.randomUUID().toString()
		val soknad = SoknadDbDataTestBuilder(brukerId = "12345678901", innsendingsId = innsendingsid, skjemanr ="NAV 20-74.13", status = SoknadsStatus.Opprettet).build()
		soknadRepository.save(soknad)
		every { sendTilPublisher.opprettBrukernotifikasjon(any()) } throws RuntimeException("Feil ved sending av brukernotifikasjon")

		// When
		assertThrows<Exception> {
			brukernotifikasjonPublisher.createNotification(soknad)
		}

		// Then
		val capturedNotification = mutableListOf<AddNotification>()
		verify(exactly = 5) { sendTilPublisher.opprettBrukernotifikasjon(capture(capturedNotification)) }

		assertTrue { capturedNotification.all { it.soknadRef.innsendingId == soknad.innsendingsid } }
	}

	@Test
	fun `sends the notification the second time after one failure`() {
		// Given
		val innsendingsid = UUID.randomUUID().toString()
		val soknad = SoknadDbDataTestBuilder(brukerId = "12345678901", innsendingsId = innsendingsid, skjemanr ="NAV 20-74.13", status = SoknadsStatus.Opprettet).build()
		soknadRepository.save(soknad)

		every { sendTilPublisher.opprettBrukernotifikasjon(any()) }
			.throws(RuntimeException("First failure"))
			.andThen(Unit)

		// When
		val notificationCreated = brukernotifikasjonPublisher.createNotification(soknad)

		// Then
		val notifications = mutableListOf<AddNotification>()
		verify(exactly = 2) { sendTilPublisher.opprettBrukernotifikasjon(capture(notifications)) }

		assertTrue { notificationCreated }
		assertEquals(2, notifications.size)
		assertTrue { notifications.all { it.soknadRef.innsendingId == soknad.innsendingsid } }
	}

	@Test
	fun `sends close notification the third time after two failures`() {
		// Given
		val innsendingsid = UUID.randomUUID().toString()
		val soknad = SoknadDbDataTestBuilder(brukerId = "12345678901", innsendingsId = innsendingsid, skjemanr ="NAV 20-74.13", status = SoknadsStatus.Innsendt).build()
		soknadRepository.save(soknad)

		every { sendTilPublisher.avsluttBrukernotifikasjon(any()) }
			.throws(RuntimeException("First failure"))
			.andThenThrows(RuntimeException("Second failure"))
			.andThen(Unit)

		// When
		val notificationCreated = brukernotifikasjonPublisher.closeNotification(soknad)

		// Then
		val notifications = mutableListOf<SoknadRef>()
		verify(exactly = 3) { sendTilPublisher.avsluttBrukernotifikasjon(capture(notifications)) }

		assertTrue { notificationCreated }
		assertEquals(3, notifications.size)
		assertTrue { notifications.all { it.innsendingId == soknad.innsendingsid } }
	}

}
