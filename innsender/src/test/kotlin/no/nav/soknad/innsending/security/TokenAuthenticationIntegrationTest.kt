package no.nav.soknad.innsending.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Azure-token valideres av Texas (#461, steg 3i). Tilgangsmatrisen dekker gyldige og ugyldige token
class TokenAuthenticationIntegrationTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var texas: FakeTokenIntrospector

	@LocalServerPort
	var serverPort: Int = 0

	private val tokens get() = TokenGenerator(mockOAuth2Server)

	@AfterEach
	fun resetTexas() {
		texas.stub = null
	}

	@Test
	fun `Texas down gives 503 for endpoints that require an Azure token`() {
		texas.stub = { _, _ -> throw TokenServiceUnavailableException("Texas svarte 503 på introspeksjon") }

		val result = get("/innsendte/v1/files/${UUID.randomUUID()}", tokens.lagAzureM2MToken(), "x-innsendingId" to UUID.randomUUID().toString())

		assertEquals(503, result.first)
		assertEquals("temporarilyUnavailable", result.second)
	}

	@Test
	fun `Texas down does not affect citizens, who are still validated by token-support`() {
		texas.stub = { _, _ -> throw TokenServiceUnavailableException("Texas svarte 503 på introspeksjon") }

		val status = get("/frontend/v1/soknad/${UUID.randomUUID()}", tokens.lagTokenXToken()).first

		assertEquals(404, status)
	}

	@Test
	fun `Texas down does not affect public routes`() {
		texas.stub = { _, _ -> throw TokenServiceUnavailableException("Texas svarte 503 på introspeksjon") }

		val status = get("/health/isAlive", tokens.lagAzureM2MToken()).first

		assertTrue(status in 200..299, "Fikk $status")
	}

	@Test
	fun `an invalid Azure token still gives 403 when Texas answers`() {
		val result = get("/innsendte/v1/files/${UUID.randomUUID()}", tokens.lagAzureM2MToken(expiry = -3600), "x-innsendingId" to UUID.randomUUID().toString())

		assertEquals(403, result.first)
		assertEquals("errorCode.forbidden", result.second)
	}

	private fun get(path: String, bearer: String, vararg headere: Pair<String, String>): Pair<Int, String?> {
		val result = webTestClient.mutate().baseUrl("http://localhost:$serverPort").build()
			.get().uri(path)
			.headers { h ->
				h.setBearerAuth(bearer)
				headere.forEach { (name, value) -> h.set(name, value) }
			}
			.exchange().returnResult(String::class.java)
		val body = result.responseBody.collectList().block()?.joinToString("").orEmpty()
		val errorCode = runCatching { jacksonObjectMapper().readTree(body)?.get("errorCode")?.asText() }.getOrNull()
		return result.status.value() to errorCode
	}
}
