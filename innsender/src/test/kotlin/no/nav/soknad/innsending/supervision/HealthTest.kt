package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.ApplicationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired


class HealthTest : ApplicationTest() {

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
			healthCheck.isAlive()
		}
	}

	@Test
	fun sjekkStatus() {
		assertDoesNotThrow {
			healthCheck.getStatus()
		}
	}

	@Test
	fun sjekkBackends() {
		assertDoesNotThrow {
			healthCheck.getBackends()
		}
	}
}
