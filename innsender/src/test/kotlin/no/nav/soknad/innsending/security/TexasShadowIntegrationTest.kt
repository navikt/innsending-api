package no.nav.soknad.innsending.security

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.token.IntrospectionResult
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
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

// Skyggemodus koblet inn i Spring Security-kjeden (#461, steg 3h). Fra steg 3j validerer Texas alle utstedere, og
// skyggemodus er av. Testen lar token-support validere TokenX for å teste skyggemodus til den fjernes i steg 4
@TestPropertySource(properties = ["texas.shadow.enabled=true", "texas.incoming.issuers=AZURE"])
class TexasShadowIntegrationTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var metrics: InnsenderMetrics

	@Autowired
	lateinit var texas: FakeTokenIntrospector

	@Autowired
	lateinit var filterChainProxy: org.springframework.security.web.FilterChainProxy

	@LocalServerPort
	var serverPort: Int = 0

	@AfterEach
	fun resetTexas() {
		texas.stub = null
	}

	@Test
	fun `equal answers from Texas and token-support are counted as a match`() {
		val before = countOf("match")

		assertEquals(404, getUnknownApplication(TokenGenerator(mockOAuth2Server).lagTokenXToken()))
		waitUntil { countOf("match") > before }
	}

	@Test
	fun `a Texas mismatch does not change the response`() {
		texas.stub = { _, _ -> IntrospectionResult(false, emptyMap()) }
		val before = countOf("texas_rejects")

		// Token-support godtok tokenet. Svaret er det vanlige for en søknad som ikke finnes, uansett hva Texas mener
		assertEquals(404, getUnknownApplication(TokenGenerator(mockOAuth2Server).lagTokenXToken()))
		waitUntil { countOf("texas_rejects") > before }
	}

	@Test
	fun `a Texas failure does not change the response`() {
		texas.stub = { _, _ -> throw TokenServiceUnavailableException("Texas svarte 500 på introspeksjon") }
		val before = countOf("texas_error")

		assertEquals(404, getUnknownApplication(TokenGenerator(mockOAuth2Server).lagTokenXToken()))
		waitUntil { countOf("texas_error") > before }
	}

	// Bro, Texas og skygge skal ligge i denne rekkefølgen, se WebSecurityConfig
	@Test
	fun `the filters in the Spring Security chain are in the right order`() {
		val filters = filterChainProxy.filterChains.single().filters.map { it.javaClass.name }
		val bridge = filters.indexOf(TokenSupportBridgeFilter::class.java.name)
		val texas = filters.indexOf(TokenAuthenticationFilter::class.java.name)
		val shadow = filters.indexOf(TexasShadowFilter::class.java.name)
		val anonymous = filters.indexOf(org.springframework.security.web.authentication.AnonymousAuthenticationFilter::class.java.name)

		kotlin.test.assertTrue(bridge in 0 until texas && texas < shadow && shadow < anonymous, "Rekkefølge: $filters")
	}

	private fun countOf(outcome: String) = metrics.getTexasShadow("tokenx", outcome) ?: 0.0

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
