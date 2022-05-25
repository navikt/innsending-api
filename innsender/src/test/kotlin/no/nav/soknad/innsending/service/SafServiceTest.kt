package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.saf.SafAPITmp
import no.nav.soknad.innsending.util.testpersonid
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement

@SpringBootTest
@ActiveProfiles("test")
@EnableTransactionManagement
class SafServiceTest {

	@Autowired
	private lateinit var restConfig: RestConfig


	@Test
	fun hentInnsendteSoknaderForBrukerTest() {
		val brukerId = testpersonid
		val safService = SafService(SafAPITmp(restConfig))
		val innsendteSoknader = safService.hentInnsendteSoknader(brukerId)

		assertTrue(innsendteSoknader.isNotEmpty())
	}

	@Test
	fun brukerHarIngenInnsendteSoknaderTest() {
		val brukerId = "999999999999"
		val safService = SafService(SafAPITmp(restConfig))
		val innsendteSoknader = safService.hentInnsendteSoknader(brukerId)

		assertTrue(innsendteSoknader.isEmpty())
	}

}
