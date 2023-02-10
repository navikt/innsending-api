package no.nav.soknad.innsending.supervision

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import no.nav.soknad.innsending.cleanup.LeaderSelectionUtility
import no.nav.soknad.innsending.repository.FilRepository
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.utils.SoknadDbDataTestdataBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
class SoknadSupervisionTest {

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@InjectMockKs
	private val innsenderMetrics = mockk<InnsenderMetrics>()

	@BeforeEach
	fun setup() {
		mockkConstructor(LeaderSelectionUtility::class)
		every { anyConstructed<LeaderSelectionUtility>().isLeader() } returns true
		every { innsenderMetrics.absentInArchive(any()) } just runs
	}

	@AfterEach
	fun cleanup() {
		soknadRepository.deleteAll()
	}

	@Test
	fun testAbsentInArchiveWhenErarkivertIsNull() {
		val soknad = SoknadDbDataTestdataBuilder().erarkivert(null).build()
		soknadRepository.save(soknad)

		val job = SoknadSupervision(soknadRepository, innsenderMetrics)
		job.run()

		verify(exactly = 1) { innsenderMetrics.absentInArchive(0) }
	}

	@Test
	fun testAbsentInArchiveWhenErarkivertIsTrue() {
		val soknad = SoknadDbDataTestdataBuilder().erarkivert(true).build()
		soknadRepository.save(soknad)

		val job = SoknadSupervision(soknadRepository, innsenderMetrics)
		job.run()

		verify(exactly = 1) { innsenderMetrics.absentInArchive(0) }
	}

	@Test
	fun testAbsentInArchiveWhenErarkivertIsFalse() {
		val soknad = SoknadDbDataTestdataBuilder().erarkivert(false).build()
		soknadRepository.save(soknad)

		val job = SoknadSupervision(soknadRepository, innsenderMetrics)
		job.run()

		verify(exactly = 1) { innsenderMetrics.absentInArchive(1) }
	}

}
