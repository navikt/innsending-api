package no.nav.soknad.innsending.consumerapis.saf

import kotlinx.coroutines.runBlocking
import no.nav.security.token.support.spring.test.EnableMockOAuth2Server
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.AssertionErrors.assertTrue

@Suppress("DEPRECATION")
@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"])
@AutoConfigureWireMock
@ActiveProfiles("test")
@ExtendWith(SpringExtension::class)
@EnableMockOAuth2Server // Tilgjengliggjør en oicd-provider for test. Se application-test.yml -> no.nav.security.jwt.issuer.selvbetjening for konfigurasjon
internal class SafAPITest {

	@Autowired
	lateinit var safAPI: SafAPI

	@Test
	internal fun testHentJournalposter() {
/*

		val journalposter = safAPI.hentBrukersSakerIArkivet("12345678901")
		assertTrue("Ingen journalposter funnet", journalposter != null)
		runBlocking {
			val journalpostListe = safAPI.getSoknadsDataForPerson("12345678901")
			assertTrue("Ingen journalposter funnet", journalpostListe != null)
		}
*/
	}

}
