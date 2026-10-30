package no.nav.soknad.innsending.security

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.texas.TexasIntrospeksjon
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals

// Skyggemodus koblet inn i Spring Security-kjeden (#461, steg 3h). TokenX valideres fortsatt av token-support
@TestPropertySource(properties = ["texas.skygge.aktiv=true"])
class TexasSkyggeIntegrasjonTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var metrics: InnsenderMetrics

	@Autowired
	lateinit var texas: FakeTexasIntrospeksjon

	@LocalServerPort
	var serverPort: Int = 0

	@AfterEach
	fun nullstillTexas() {
		texas.overstyring = null
	}

	@Test
	fun `like svar fra Texas og token-support telles som samsvar`() {
		val før = antall("samsvar")

		assertEquals(404, hentUkjentSoknad(TokenGenerator(mockOAuth2Server).lagTokenXToken()))
		ventPå { antall("samsvar") > før }
	}

	@Test
	fun `avvik fra Texas endrer ikke svaret`() {
		texas.overstyring = { _, _ -> TexasIntrospeksjon(false, emptyMap()) }
		val før = antall("texas_avviser")

		// Token-support godtok tokenet. Svaret er det vanlige for en søknad som ikke finnes, uansett hva Texas mener
		assertEquals(404, hentUkjentSoknad(TokenGenerator(mockOAuth2Server).lagTokenXToken()))
		ventPå { antall("texas_avviser") > før }
	}

	@Test
	fun `feil mot Texas endrer ikke svaret`() {
		texas.overstyring = { _, _ -> throw TexasUnavailableException("Texas svarte 500 på introspeksjon") }
		val før = antall("texas_feil")

		assertEquals(404, hentUkjentSoknad(TokenGenerator(mockOAuth2Server).lagTokenXToken()))
		ventPå { antall("texas_feil") > før }
	}

	private fun antall(utfall: String) = metrics.getTexasSkygge("tokenx", utfall) ?: 0.0

	private fun hentUkjentSoknad(token: String): Int =
		webTestClient.mutate().baseUrl("http://localhost:$serverPort").build()
			.get().uri("/frontend/v1/soknad/${UUID.randomUUID()}")
			.headers { it.setBearerAuth(token) }
			.exchange().returnResult(String::class.java).status.value()

	private fun ventPå(betingelse: () -> Boolean) {
		val frist = System.nanoTime() + Duration.ofSeconds(10).toNanos()
		while (!betingelse()) {
			check(System.nanoTime() < frist) { "Skyggesammenligningen ble ikke telt innen 10 sekunder" }
			Thread.sleep(50)
		}
	}
}
