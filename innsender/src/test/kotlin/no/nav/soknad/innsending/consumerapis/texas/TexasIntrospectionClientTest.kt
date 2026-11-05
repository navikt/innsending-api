package no.nav.soknad.innsending.consumerapis.texas

import no.nav.soknad.innsending.consumerapis.token.IntrospectionResult
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.security.TokenIssuer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TexasIntrospectionClientTest {

	private val endpoint = "http://texas.test/api/v1/introspect"
	private val token = "eyJhbGciOiJSUzI1NiJ9.innkommende-token-med-pid.signatur"

	private lateinit var server: MockRestServiceServer
	private lateinit var client: TexasIntrospectionClient

	@BeforeEach
	fun setUp() {
		val builder = RestClient.builder()
		server = MockRestServiceServer.bindTo(builder).build()
		client = TexasIntrospectionClient(builder.build(), endpoint)
	}

	@Test
	fun `a valid token gives the claims without active`() {
		server.expect(requestTo(endpoint))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("""{"identity_provider":"tokenx","token":"$token"}""", true))
			.andRespond(withSuccess("""{"active":true,"pid":"12345678901","acr":"idporten-loa-high"}""", MediaType.APPLICATION_JSON))

		val result = client.introspect(TokenIssuer.TOKENX, token)

		assertTrue(result.active)
		assertEquals(mapOf("pid" to "12345678901", "acr" to "idporten-loa-high"), result.claims)
		server.verify()
	}

	@Test
	fun `an invalid token gives neither claims nor the error text from Texas`() {
		server.expect(requestTo(endpoint))
			.andRespond(withSuccess("""{"active":false,"error":"ugyldig token $token for 12345678901"}""", MediaType.APPLICATION_JSON))

		val result = client.introspect(TokenIssuer.AZURE, token)

		assertFalse(result.active)
		assertTrue(result.claims.isEmpty())
		assertFalse(result.toString().contains(token))
		assertFalse(result.toString().contains("12345678901"))
	}

	@Test
	fun `an error status from Texas gives TokenServiceUnavailableException without token and body`() {
		server.expect(requestTo(endpoint))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("intern feil med $token"))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.introspect(TokenIssuer.TOKENX, token) }

		assertEquals("Texas svarte 500 på introspeksjon for identity_provider=tokenx", error.message)
		assertNull(error.cause)
	}

	@Test
	fun `a timeout gives TokenServiceUnavailableException without cause`() {
		server.expect(requestTo(endpoint)).andRespond(withException(SocketTimeoutException("Read timed out")))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.introspect(TokenIssuer.TOKENX, token) }

		assertFalse(error.message!!.contains(token))
		assertNull(error.cause)
	}

	@Test
	fun `a response without active is an error`() {
		server.expect(requestTo(endpoint)).andRespond(withSuccess("""{"pid":"12345678901"}""", MediaType.APPLICATION_JSON))

		assertFailsWith<TokenServiceUnavailableException> { client.introspect(TokenIssuer.TOKENX, token) }
	}

	@Test
	fun `toString does not show claims`() {
		assertFalse(IntrospectionResult(true, mapOf("pid" to "12345678901")).toString().contains("12345678901"))
	}
}
