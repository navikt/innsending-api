package no.nav.soknad.innsending.security

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.util.UUID
import kotlin.test.assertTrue

// Skyggemodus koblet inn i Spring Security-kjeden (#461, steg 3h)
@TestPropertySource(properties = ["texas.skygge.aktiv=true"])
class TexasSkyggeIntegrasjonTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var metrics: InnsenderMetrics

	@LocalServerPort
	var serverPort: Int = 0

	@Test
	fun `Texas sammenlignes med token-support uten å påvirke svaret`() {
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()
		stubFor(
			post(urlEqualTo("/texas/api/v1/introspect"))
				.withRequestBody(equalToJson("""{"identity_provider":"tokenx","token":"$token"}"""))
				.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""{"active":false,"error":"test"}"""))
		)
		val før = metrics.getTexasSkygge("tokenx", "texas_avviser") ?: 0.0

		val status = webTestClient.mutate().baseUrl("http://localhost:$serverPort").build()
			.get().uri("/frontend/v1/soknad/${UUID.randomUUID()}")
			.headers { it.setBearerAuth(token) }
			.exchange().returnResult(String::class.java).status.value()

		// Token-support godtok tokenet, og Texas sitt avvik skal ikke gi avvisning
		assertTrue(status !in setOf(401, 403, 503), "Fikk $status")
		ventPå { (metrics.getTexasSkygge("tokenx", "texas_avviser") ?: 0.0) > før }
	}

	private fun ventPå(betingelse: () -> Boolean) {
		val frist = System.nanoTime() + Duration.ofSeconds(10).toNanos()
		while (!betingelse()) {
			check(System.nanoTime() < frist) { "Skyggesammenligningen ble ikke telt innen 10 sekunder" }
			Thread.sleep(50)
		}
	}
}
