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
	private val safService = mockk<SafService>()

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
		soknadRepository, safService, innsenderMetrics
	)

/*
	@Test
	fun testAtSoknadSomIkkeEksistererIArkivetBlirMarkertSomIkkeArkivert() {
		val innsendtdato = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato).build()
		soknadRepository.save(soknad)

		every { safService.hentArkiverteSaker(soknad.brukerid) } returns emptyList()

		val service = lagScheduledOperationsService()
		service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(false, lagretSoknad.get().erarkivert)

		verify { innsenderMetrics.absentInArchive(1) }
	}
*/

/*
	@Test
	fun testAtSoknadSomEksistererIArkivetBlirMarkertSomArkivert() {
		val innsendtdato = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato).build()
		soknadRepository.save(soknad)

		val sak = ArkivertSakerTestdataBuilder().fromSoknad(soknad).build()
		every { safService.hentArkiverteSaker(soknad.brukerid) } returns listOf(sak)

		val service = lagScheduledOperationsService()
		service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(true, lagretSoknad.get().erarkivert)

		verify { innsenderMetrics.absentInArchive(0) }
	}
*/

	/*
	@Test
	fun testAtSoknadSomNyligHarBlittSendtInnIkkeBlirProsessert() {
		val innsendtdato = LocalDateTime.now().minusMinutes(1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato).build()
		soknadRepository.save(soknad)

		val sak = ArkivertSakerTestdataBuilder().fromSoknad(soknad).build()
		every { safService.hentArkiverteSaker(soknad.brukerid) } returns listOf(sak)

		val service = lagScheduledOperationsService()
		service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)

		verify { safService wasNot called }

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertNull(lagretSoknad.get().erarkivert)

		verify { innsenderMetrics.absentInArchive(0) }
	}
*/

/*
	@Test
	fun testAtSoknadAIkkeMarkeresSomArkivertOgSoknadBMarkeresSomArkivert() {
		val innsendtdatoA = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknadA = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdatoA).build()
		soknadRepository.save(soknadA)

		val innsendtdatoB = LocalDateTime.now().minusHours(OFFSET_HOURS + 2)
		val soknadB = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdatoB, brukerId = soknadA.brukerid).build()
		soknadRepository.save(soknadB)

		val sakB = ArkivertSakerTestdataBuilder().fromSoknad(soknadB).build()
		every { safService.hentArkiverteSaker(soknadA.brukerid) } returns listOf(sakB)

		val service = lagScheduledOperationsService()
		service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)

		val lagretSoknadA = soknadRepository.findById(soknadA.id!!)
		assertTrue(lagretSoknadA.isPresent)
		assertEquals(false, lagretSoknadA.get().erarkivert)

		val lagretSoknadB = soknadRepository.findById(soknadB.id!!)
		assertTrue(lagretSoknadB.isPresent)
		assertEquals(true, lagretSoknadB.get().erarkivert)

		verify { innsenderMetrics.absentInArchive(1) }
		verify(exactly = 1) { safService.hentArkiverteSaker(soknadA.brukerid) }
	}
*/

	@Test
	fun testAtSoknadSomForstMarkeresSomIkkeArkivertMarkeresSomArkivertVedNesteSjekk() {
		val innsendtdato = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato).build()
		soknadRepository.save(soknad)

		val sak = ArkivertSakerTestdataBuilder().fromSoknad(soknad).build()
		every { safService.hentArkiverteSaker(soknad.brukerid) } returns emptyList() andThen listOf(sak)

		val service = lagScheduledOperationsService()

		service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)
		val lagretSoknad1 = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad1.isPresent)
		assertEquals(false, lagretSoknad1.get().erarkivert)

		service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)
		val lagretSoknad2 = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad2.isPresent)
		assertEquals(true, lagretSoknad2.get().erarkivert)

		verifySequence {
			innsenderMetrics.absentInArchive(1)
			innsenderMetrics.absentInArchive(0)
		}
	}

	@Test
	fun testAtExceptionKastesDersomKallMotSafFeiler() {
		val innsendtdato = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato).build()
		soknadRepository.save(soknad)

		every { safService.hentArkiverteSaker(soknad.brukerid) } throws Exception("Test :: Saf is unavailable")

		var exceptionThrown = false;
		val service = lagScheduledOperationsService()
		try {
			service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)
		} catch (e: Exception) {
			exceptionThrown = true
		}
		assertTrue(exceptionThrown);

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertNull(lagretSoknad.get().erarkivert)

		verify(exactly = 0) { innsenderMetrics.absentInArchive(any()) }
	}

	@Test
	fun testAtSoknadInnsendtIForkantAvTimespanOgMarkertSomIkkeArkivertTellesMedVedRegistreringAvMetrics() {
		val innsendtdato = LocalDateTime.now().minusHours(TIMESPAN_HOURS + OFFSET_HOURS + 2)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato, erarkivert = false).build()
		soknadRepository.save(soknad)

		val sak = ArkivertSakerTestdataBuilder().fromSoknad(soknad).build()
		every { safService.hentArkiverteSaker(soknad.brukerid) } returns listOf(sak)

		val service = lagScheduledOperationsService()
		service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)

		verify { safService wasNot called }

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(false, lagretSoknad.get().erarkivert)

		verify { innsenderMetrics.absentInArchive(1) }
	}

/*
	@Test
	fun testAtSoknadSomAlleredeErMarkertSomArkivertIkkeProsesseresIgjen() {
		val innsendtdato = LocalDateTime.now().minusHours(OFFSET_HOURS + 1)
		val soknad = SoknadDbDataTestdataBuilder(innsendtdato = innsendtdato, erarkivert = true).build()
		soknadRepository.save(soknad)

		val sak = ArkivertSakerTestdataBuilder().fromSoknad(soknad).build()
		every { safService.hentArkiverteSaker(soknad.brukerid) } returns listOf(sak)

		val service = lagScheduledOperationsService()
		service.updateSoknadErArkivert(TIMESPAN_HOURS, OFFSET_HOURS)

		verify { safService wasNot called }

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(true, lagretSoknad.get().erarkivert)
	}
*/

}
