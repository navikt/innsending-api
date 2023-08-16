package no.nav.soknad.innsending.service

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.repository.FilRepository
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.VedleggRepository
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

private const val OFFSET_MINUTES = 95L

class ScheduledOperationsServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@Autowired
	private lateinit var filRepository: FilRepository

	@InjectMockKs
	private val innsenderMetrics = mockk<InnsenderMetrics>()

	@BeforeEach
	fun setup() {
		every { innsenderMetrics.absentInArchive(any()) } just runs
		every { innsenderMetrics.archivingFailedSet(any()) } just runs
	}

	@AfterEach
	fun cleanup() {
		filRepository.deleteAll()
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
	}

	private fun lagScheduledOperationsService() = ScheduledOperationsService(
		soknadRepository, innsenderMetrics
	)

	@Test
	fun testAtSoknadSomIkkeEksistererIArkivetBlirMarkertSomIkkeArkivert() {
		val innsendtdato = LocalDateTime.now().minusMinutes(OFFSET_MINUTES + 1)
		val soknad = SoknadDbDataTestBuilder(innsendtdato = innsendtdato, status = SoknadsStatus.Innsendt).build()
		soknadRepository.save(soknad)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(OFFSET_MINUTES)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertTrue(lagretSoknad.get().arkiveringsstatus == ArkiveringsStatus.IkkeSatt)

		verify { innsenderMetrics.absentInArchive(1) }
	}

	@Test
	fun testAtSoknadSomEksistererIArkivetBlirMarkertSomArkivert() {
		val innsendtdato = LocalDateTime.now().minusMinutes(OFFSET_MINUTES + 1)
		val soknad = SoknadDbDataTestBuilder(innsendtdato = innsendtdato, status = SoknadsStatus.Innsendt).build()
		soknadRepository.save(soknad)
		simulateKafkaPolling(true, soknad.innsendingsid)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(OFFSET_MINUTES)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(ArkiveringsStatus.Arkivert, lagretSoknad.get().arkiveringsstatus)

		verify { innsenderMetrics.absentInArchive(0) }
	}

	@Test
	fun testAtInnsendtSoknadDerArkiveringHarFeiletBlirRapportert() {
		val innsendtdato = LocalDateTime.now().minusMinutes(OFFSET_MINUTES + 1)
		val soknad = SoknadDbDataTestBuilder(innsendtdato = innsendtdato, status = SoknadsStatus.Innsendt).build()
		soknadRepository.save(soknad)
		simulateKafkaPolling(false, soknad.innsendingsid)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(OFFSET_MINUTES)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, lagretSoknad.get().arkiveringsstatus)

		verify {
			innsenderMetrics.absentInArchive(0)
			innsenderMetrics.archivingFailedSet(1)
		}
	}

	@Test
	fun testAtSoknadAIkkeMarkeresSomArkivertOgSoknadBMarkeresSomArkivert() {
		val innsendtdatoA = LocalDateTime.now().minusMinutes(OFFSET_MINUTES + 1)
		val soknadA = SoknadDbDataTestBuilder(innsendtdato = innsendtdatoA, status = SoknadsStatus.Innsendt).build()
		soknadRepository.save(soknadA)
		simulateKafkaPolling(true, soknadA.innsendingsid)

		val innsendtdatoB = LocalDateTime.now().minusMinutes(OFFSET_MINUTES + 2)
		val soknadB = SoknadDbDataTestBuilder(
			innsendtdato = innsendtdatoB,
			brukerId = soknadA.brukerid,
			status = SoknadsStatus.Innsendt
		).build()
		soknadRepository.save(soknadB)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(OFFSET_MINUTES)

		verify { innsenderMetrics.absentInArchive(1) }
	}

	@Test
	fun testAtSoknadSomForstMarkeresSomIkkeArkivertMarkeresSomArkivertVedNesteSjekk() {
		val innsendtdato = LocalDateTime.now().minusMinutes(OFFSET_MINUTES + 1)
		val soknad = SoknadDbDataTestBuilder(innsendtdato = innsendtdato, status = SoknadsStatus.Innsendt).build()
		soknadRepository.save(soknad)

		val service = lagScheduledOperationsService()
		val soknadId = soknad.id!!

		service.checkIfApplicationsAreArchived(OFFSET_MINUTES)
		val lagretSoknad1 = soknadRepository.findById(soknadId)
		assertTrue(lagretSoknad1.isPresent)
		assertTrue(lagretSoknad1.get().arkiveringsstatus == ArkiveringsStatus.IkkeSatt)

		simulateKafkaPolling(true, soknad.innsendingsid)

		service.checkIfApplicationsAreArchived(OFFSET_MINUTES)
		val lagretSoknad2 = soknadRepository.findById(soknadId)
		assertTrue(lagretSoknad2.isPresent)
		assertEquals(ArkiveringsStatus.Arkivert, lagretSoknad2.get().arkiveringsstatus)

		verifySequence {
			innsenderMetrics.absentInArchive(1)
			innsenderMetrics.archivingFailedSet(0)
			innsenderMetrics.absentInArchive(0)
			innsenderMetrics.archivingFailedSet(0)
		}
	}


	@Test
	fun testAtSoknadInnsendtIForkantAvTimespanOgMarkertSomIkkeArkivertTellesMedVedRegistreringAvMetrics() {
		val innsendtdato = LocalDateTime.now().minusMinutes(OFFSET_MINUTES + 2)
		val soknad =
			SoknadDbDataTestBuilder(
				innsendtdato = innsendtdato,
				arkiveringsStatus = ArkiveringsStatus.IkkeSatt,
				status = SoknadsStatus.Innsendt
			).build()
		soknadRepository.save(soknad)

		val service = lagScheduledOperationsService()
		service.checkIfApplicationsAreArchived(OFFSET_MINUTES)

		val lagretSoknad = soknadRepository.findById(soknad.id!!)
		assertTrue(lagretSoknad.isPresent)
		assertEquals(ArkiveringsStatus.IkkeSatt, lagretSoknad.get().arkiveringsstatus)

		verify { innsenderMetrics.absentInArchive(1) }
	}

	private fun simulateKafkaPolling(ok: Boolean, innsendingId: String) {
		soknadRepository.updateArkiveringsStatus(
			(if (ok) ArkiveringsStatus.Arkivert else ArkiveringsStatus.ArkiveringFeilet),
			listOf(innsendingId)
		)
	}

}
