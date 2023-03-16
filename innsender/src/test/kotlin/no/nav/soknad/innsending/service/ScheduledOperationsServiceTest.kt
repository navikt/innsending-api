package no.nav.soknad.innsending.service

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.ArkivertSakerTestdataBuilder
import no.nav.soknad.innsending.utils.SoknadDbDataTestdataBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.time.LocalDateTime

private const val TIMESPAN_HOURS = 24L
private const val OFFSET_HOURS = 2L

@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
class ScheduledOperationsServiceTest {

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@InjectMockKs
	private val innsenderMetrics = mockk<InnsenderMetrics>()

	@BeforeEach
	fun setup() {
		every { innsenderMetrics.absentInArchive(any()) } just runs
	}

	@AfterEach
	fun cleanup() {
		soknadRepository.deleteAll()
	}

	private fun lagScheduledOperationsService() = ScheduledOperationsService(
		soknadRepository, innsenderMetrics
	)

	@Test
	fun testAtSoknadSomIkkeEksistererIArkivetBlirMarkertSomIkkeArkivert() {
		val innsendtdato = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato).build()
		soknadRepository.save(soknad)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(TIMESPAN_HOURS, OFFSET_HOURS)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertTrue(lagretSoknad.get().erarkivert == null || lagretSoknad.get().erarkivert == false)

		verify { innsenderMetrics.absentInArchive(1) }
	}

	@Test
	fun testAtSoknadSomEksistererIArkivetBlirMarkertSomArkivert() {
		val innsendtdato = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato).build()
		soknadRepository.save(soknad)
		simulateKafkaPolling(true, soknad.innsendingsid)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(TIMESPAN_HOURS, OFFSET_HOURS)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(true, lagretSoknad.get().erarkivert)

		verify { innsenderMetrics.absentInArchive(0) }
	}


	@Test
	fun testAtSoknadAIkkeMarkeresSomArkivertOgSoknadBMarkeresSomArkivert() {
		val innsendtdatoA = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknadA = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdatoA).build()
		soknadRepository.save(soknadA)
		simulateKafkaPolling(true, soknadA.innsendingsid)

		val innsendtdatoB = LocalDateTime.now().minusHours(OFFSET_HOURS + 2)
		val soknadB = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdatoB, brukerId = soknadA.brukerid).build()
		soknadRepository.save(soknadB)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(TIMESPAN_HOURS, OFFSET_HOURS)

		verify { innsenderMetrics.absentInArchive(1) }
	}

	@Test
	fun testAtSoknadSomForstMarkeresSomIkkeArkivertMarkeresSomArkivertVedNesteSjekk() {
		val innsendtdato = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato).build()
		soknadRepository.save(soknad)

		val service = lagScheduledOperationsService()

		service.checkIfApplicationsAreArchived(TIMESPAN_HOURS, OFFSET_HOURS)
		val lagretSoknad1 = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad1.isPresent)
		assertTrue(lagretSoknad1.get().erarkivert == null || lagretSoknad1.get().erarkivert == false)

		simulateKafkaPolling(true, soknad.innsendingsid)

		service.checkIfApplicationsAreArchived(TIMESPAN_HOURS, OFFSET_HOURS)
		val lagretSoknad2 = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad2.isPresent)
		assertEquals(true, lagretSoknad2.get().erarkivert)

		verifySequence {
			innsenderMetrics.absentInArchive(1)
			innsenderMetrics.absentInArchive(0)
		}
	}


	@Test
	fun testAtSoknadInnsendtIForkantAvTimespanOgMarkertSomIkkeArkivertTellesMedVedRegistreringAvMetrics() {
		val innsendtdato = LocalDateTime.now().minusHours(TIMESPAN_HOURS + OFFSET_HOURS + 2)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato, erarkivert = false).build()
		soknadRepository.save(soknad)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(TIMESPAN_HOURS, OFFSET_HOURS)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(false, lagretSoknad.get().erarkivert)

		verify { innsenderMetrics.absentInArchive(1) }
	}

	private fun simulateKafkaPolling(ok: Boolean, innsendingId: String) {
		soknadRepository.updateErArkivert(ok, listOf(innsendingId))
	}


}
