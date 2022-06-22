package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.repository.VedleggRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
class HealthTest {

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
}
