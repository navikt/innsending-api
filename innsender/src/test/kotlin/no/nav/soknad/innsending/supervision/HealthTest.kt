package no.nav.soknad.innsending.supervision

import io.mockk.every
import io.mockk.mockk
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.ApplicationStatusType
import no.nav.soknad.innsending.repository.AliveRepository
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HealthTest {

	private val pdl = mockk<HealthRequestInterface>()
	private val saf = mockk<HealthRequestInterface>()
	private val mottaker = mockk<HealthRequestInterface>()
	private val notifikasjon = mockk<HealthRequestInterface>()
	private val aliveRepository = mockk<AliveRepository>()

	private val healthCheck = HealthCheck(pdl, saf, mottaker, notifikasjon, aliveRepository, "https://logs.example")

	private fun allBackendsReady() {
		listOf(pdl, saf, mottaker, notifikasjon).forEach { every { it.isReady() } returns "ok" }
		every { aliveRepository.findTestById(1L) } returns "ok"
	}

	@Test
	fun `isAlive and isReady answer ok`() {
		assertEquals("ok", healthCheck.isAlive().body)
		assertEquals("ok", healthCheck.isReady().body)
	}

	@Test
	fun `status is OK with a link to the logs`() {
		val status = healthCheck.getStatus().body!!

		assertEquals(ApplicationStatusType.OK, status.status)
		assertEquals("https://logs.example", status.logLink)
	}

	@Test
	fun `backends are OK when every dependency is ready`() {
		allBackendsReady()

		assertEquals(ApplicationStatusType.OK, healthCheck.getBackends().body!!.status)
	}

	@Test
	fun `an unavailable PDL is reported as an issue`() {
		allBackendsReady()
		every { pdl.isReady() } returns "down"

		assertEquals(ApplicationStatusType.ISSUE, healthCheck.getBackends().body!!.status)
	}

	@Test
	fun `an unavailable soknadsmottaker is reported as down even when PDL also fails`() {
		allBackendsReady()
		every { pdl.isReady() } returns "down"
		every { mottaker.isReady() } returns "down"

		assertEquals(ApplicationStatusType.DOWN, healthCheck.getBackends().body!!.status)
	}
}
