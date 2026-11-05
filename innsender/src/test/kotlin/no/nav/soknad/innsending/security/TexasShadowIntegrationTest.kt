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
import kotlin.test.assertEquals

// Skyggemodus koblet inn i Spring Security-kjeden (#461, steg 3h)
@TestPropertySource(properties = ["texas.shadow.enabled=true"])
class TexasShadowIntegrationTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var metrics: InnsenderMetrics

	@LocalServerPort
	var serverPort: Int = 0

	@Test
	fun `Texas is compared with token-support without affecting the response`() {
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()
		stubFor(
			post(urlEqualTo("/texas/api/v1/introspect"))
				.withRequestBody(equalToJson("""{"identity_provider":"tokenx","token":"$token"}"""))
				.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("""{"active":false,"error":"test"}"""))
		)
		val before = metrics.getTexasShadow("tokenx", "texas_rejects") ?: 0.0

		val status = getUnknownApplication(token)

		// Token-support godtok tokenet. Svaret er det vanlige for en søknad som ikke finnes, uansett hva Texas mener
		assertEquals(404, status)
		waitUntil { (metrics.getTexasShadow("tokenx", "texas_rejects") ?: 0.0) > before }
	}

	@Test
	fun `a Texas failure does not change the response`() {
		val token = TokenGenerator(mockOAuth2Server).lagTokenXToken()
		stubFor(
			post(urlEqualTo("/texas/api/v1/introspect"))
				.withRequestBody(equalToJson("""{"identity_provider":"tokenx","token":"$token"}"""))
				.willReturn(aResponse().withStatus(500))
		)
		val before = metrics.getTexasShadow("tokenx", "texas_error") ?: 0.0

		assertEquals(404, getUnknownApplication(token))
		waitUntil { (metrics.getTexasShadow("tokenx", "texas_error") ?: 0.0) > before }
	}

	private fun getUnknownApplication(token: String): Int =
		webTestClient.mutate().baseUrl("http://localhost:$serverPort").build()
			.get().uri("/frontend/v1/soknad/${UUID.randomUUID()}")
			.headers { it.setBearerAuth(token) }
			.exchange().returnResult(String::class.java).status.value()

	private fun waitUntil(condition: () -> Boolean) {
		val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
		while (!condition()) {
			check(System.nanoTime() < deadline) { "Skyggesammenligningen ble ikke telt innen 10 sekunder" }
			Thread.sleep(50)
		}
	}
}
