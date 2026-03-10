package no.nav.soknad.innsending.supervision

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.cleanup.LeaderSelection
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.ScheduledOperationsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SendInSupervisionTest {

	private val soknadRepository: SoknadRepository = mockk<SoknadRepository>()

	private lateinit var scheduledOperationsService: ScheduledOperationsService

	private var metrics: InnsenderMetrics = mockk<InnsenderMetrics>()

	private var leaderSelection: LeaderSelection = mockk<LeaderSelection>()

	private lateinit var sendInSupervision: SendInSupervision

	private var innsendingService: InnsendingService = mockk<InnsendingService>()


	@BeforeEach
	fun setup() {
		clearAllMocks()
		every { leaderSelection.isLeader() } returns true
		every { metrics.setNotSentIn(any()) } returns Unit
		every { innsendingService.sendInnForArkivering(any()) } returns Unit

		every { soknadRepository.countNotSentInApplications(any()) } returns 3
		every { soknadRepository.findNotSentIntApplications(any()) } returns notSentInApplications(3)

		scheduledOperationsService = ScheduledOperationsService(soknadRepository, metrics)
		sendInSupervision = SendInSupervision(
			leaderSelection = leaderSelection,
			scheduledOperationsService = scheduledOperationsService,
			offsetMinutes = -1,
			innsendingService = innsendingService
		)
	}

	private fun notSentInApplications(noOfApplications: Int): List<String> {
		val soknader = mutableListOf<String>()
		for (i in 1..noOfApplications) {soknader.add(UUID.randomUUID().toString())}
		return soknader
	}

	@Test
	fun `should trigger resending all not sent in applications`() {
		sendInSupervision.runSendInSupervision()

		verify(exactly = 1) { soknadRepository.countNotSentInApplications((any() )) }
		verify(exactly = 1) { metrics.setNotSentIn(3) }
		verify(exactly = 3) { innsendingService.sendInnForArkivering(any()) }

	}

}
