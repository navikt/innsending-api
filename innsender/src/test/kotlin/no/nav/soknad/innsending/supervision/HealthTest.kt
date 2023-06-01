package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.repository.VedleggRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier


class HealthTest : ApplicationTest() {

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@Autowired
	@Qualifier("pdl")
	private lateinit var pdl: HealthRequestInterface

	@Autowired
	@Qualifier("saf")
	private lateinit var saf: HealthRequestInterface

	@Autowired
	@Qualifier("fillager")
	private lateinit var fillager: HealthRequestInterface

	@Autowired
	@Qualifier("mottaker")
	private lateinit var mottaker: HealthRequestInterface

	@Autowired
	private lateinit var healthCheck: HealthCheck

	@Test
	fun sjekkIsReady() {
		assertDoesNotThrow {
			healthCheck.isReady()
		}

	}

	@Test
	fun sjekkIsAlive() {
		assertDoesNotThrow {
			healthCheck.isReady()
		}

	}

	@Test
	fun sjekkStatus() {
		assertDoesNotThrow {
			healthCheck.getStatus()
		}
	}
}
