package no.nav.soknad.innsending.brukernotifikasjon.kafka

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.service.NotificationService
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.lang.Thread.sleep
import kotlin.test.assertFalse
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
		val innsendingsid = "123456"
		val soknad = SoknadDbDataTestBuilder(brukerId = "12345678901", innsendingsId = innsendingsid, skjemanr ="NAV 20-74.13", status = SoknadsStatus.Opprettet).build()
		soknadRepository.save(soknad)
		every { sendTilPublisher.opprettBrukernotifikasjon(any()) } throws RuntimeException("Feil ved sending av brukernotifikasjon")

		// When
		notificationService.create(soknad.innsendingsid)

		// Then
		sleep(5000) // Liten delay for å sikre at asynkrone operasjoner er fullført før verifisering
		val capturedSoknad = mutableListOf<SoknadDbData>()
		verify(exactly = 3) { brukernotifikasjonPublisher.createNotification((capture(capturedSoknad))) }

		assertTrue { capturedSoknad.all { it.innsendingsid == soknad.innsendingsid } }
	}

	@Test
	fun `sends the notification the second time after one failure`() {
		val innsendingsid = "123456"

		every { sendTilPublisher.opprettBrukernotifikasjon(any()) }
			.throws(RuntimeException("First failure"))
			.andThen(Unit)

		val soknad = SoknadDbDataTestBuilder(innsendingsId = innsendingsid).build()
		val notificationCreated = brukernotifikasjonPublisher.createNotification(soknad)

		sleep(50) // Liten delay for å sikre at asynkrone operasjoner er fullført før verifisering
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

		sleep(50) // Liten delay for å sikre at asynkrone operasjoner er fullført før verifisering
		val notifications = mutableListOf<AddNotification>()
		verify(exactly = 3) { sendTilPublisher.opprettBrukernotifikasjon(capture(notifications)) }

		assertTrue { notificationCreated }
		assertEquals(3, notifications.size)
		assertTrue { notifications.all { it.soknadRef.innsendingId == soknad.innsendingsid } }
	}

}
