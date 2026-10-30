package no.nav.soknad.innsending.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.utils.TokenGenerator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Azure-token valideres av Texas (#461, steg 3i). Tilgangsmatrisen dekker gyldige og ugyldige token
class TexasAzureIntegrasjonTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var texas: FakeTexasIntrospeksjon

	@LocalServerPort
	var serverPort: Int = 0

	private val tokens get() = TokenGenerator(mockOAuth2Server)

	@AfterEach
	fun nullstillTexas() {
		texas.overstyring = null
	}

	@Test
	fun `Texas nede gir 503 for endepunkter som krever Azure-token`() {
		texas.overstyring = { _, _ -> throw TexasUnavailableException("Texas svarte 503 på introspeksjon") }

		val svar = get("/innsendte/v1/files/${UUID.randomUUID()}", tokens.lagAzureM2MToken(), "x-innsendingId" to UUID.randomUUID().toString())

		assertEquals(503, svar.first)
		assertEquals("temporarilyUnavailable", svar.second)
	}

	@Test
	fun `Texas nede påvirker ikke innbyggere, som fortsatt valideres av token-support`() {
		texas.overstyring = { _, _ -> throw TexasUnavailableException("Texas svarte 503 på introspeksjon") }

		val status = get("/frontend/v1/soknad/${UUID.randomUUID()}", tokens.lagTokenXToken()).first

		assertEquals(404, status)
	}

	@Test
	fun `Texas nede påvirker ikke offentlige ruter`() {
		texas.overstyring = { _, _ -> throw TexasUnavailableException("Texas svarte 503 på introspeksjon") }

		val status = get("/health/isAlive", tokens.lagAzureM2MToken()).first

		assertTrue(status in 200..299, "Fikk $status")
	}

	@Test
	fun `ugyldig Azure-token gir fortsatt 403 når Texas svarer`() {
		val svar = get("/innsendte/v1/files/${UUID.randomUUID()}", tokens.lagAzureM2MToken(expiry = -3600), "x-innsendingId" to UUID.randomUUID().toString())

		assertEquals(403, svar.first)
		assertEquals("errorCode.forbidden", svar.second)
	}

	private fun get(path: String, bearer: String, vararg headere: Pair<String, String>): Pair<Int, String?> {
		val result = webTestClient.mutate().baseUrl("http://localhost:$serverPort").build()
			.get().uri(path)
			.headers { h ->
				h.setBearerAuth(bearer)
				headere.forEach { (navn, verdi) -> h.set(navn, verdi) }
			}
			.exchange().returnResult(String::class.java)
		val body = result.responseBody.collectList().block()?.joinToString("").orEmpty()
		val errorCode = runCatching { jacksonObjectMapper().readTree(body)?.get("errorCode")?.asText() }.getOrNull()
		return result.status.value() to errorCode
	}
}
