package no.nav.soknad.innsending.consumerapis.texas

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import no.nav.soknad.innsending.consumerapis.token.IntrospectionResult
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.security.TokenIssuer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import java.net.SocketTimeoutException
import java.time.Duration
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
		client = TexasIntrospectionClient(builder.build(), endpoint, retryWait = Duration.ZERO)
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

	@ParameterizedTest
	@ValueSource(ints = [400, 401, 500])
	fun `an error status from Texas gives TokenServiceUnavailableException without retry, token or body`(status: Int) {
		server.expect(once(), requestTo(endpoint))
			.andRespond(withStatus(HttpStatus.valueOf(status)).body("feil med $token"))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.introspect(TokenIssuer.TOKENX, token) }

		assertEquals("Texas svarte $status på introspeksjon for identity_provider=tokenx", error.message)
		assertNull(error.cause)
		server.verify()
	}

	@ParameterizedTest
	@ValueSource(ints = [502, 503, 504])
	fun `a transient error status from Texas is retried once and then gives TokenServiceUnavailableException`(status: Int) {
		server.expect(times(2), requestTo(endpoint))
			.andRespond(withStatus(HttpStatus.valueOf(status)).body("feil med $token"))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.introspect(TokenIssuer.TOKENX, token) }

		assertEquals("Texas svarte $status på introspeksjon for identity_provider=tokenx", error.message)
		assertNull(error.cause)
		server.verify()
	}

	@Test
	fun `a timeout is retried once and then gives TokenServiceUnavailableException without cause`() {
		server.expect(times(2), requestTo(endpoint)).andRespond(withException(SocketTimeoutException("Read timed out")))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.introspect(TokenIssuer.TOKENX, token) }

		assertFalse(error.message!!.contains(token))
		assertNull(error.cause)
		server.verify()
	}

	@Test
	fun `a valid token is accepted when the retry after a network error succeeds`() {
		server.expect(once(), requestTo(endpoint)).andRespond(withException(SocketTimeoutException("Connection reset")))
		server.expect(once(), requestTo(endpoint))
			.andRespond(withSuccess("""{"active":true,"pid":"12345678901"}""", MediaType.APPLICATION_JSON))

		val result = client.introspect(TokenIssuer.TOKENX, token)

		assertTrue(result.active)
		server.verify()
	}

	@Test
	fun `an inactive token is not retried`() {
		server.expect(once(), requestTo(endpoint))
			.andRespond(withSuccess("""{"active":false,"error":"token expired"}""", MediaType.APPLICATION_JSON))

		assertFalse(client.introspect(TokenIssuer.TOKENX, token).active)
		server.verify()
	}

	@ParameterizedTest
	@ValueSource(strings = ["""{"pid":"12345678901"}""", """{"active":true ikke json"""])
	fun `a response without active or with invalid JSON is an error without retry`(body: String) {
		server.expect(once(), requestTo(endpoint)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

		assertFailsWith<TokenServiceUnavailableException> { client.introspect(TokenIssuer.TOKENX, token) }
		server.verify()
	}

	@Test
	fun `the retry log contains neither the token nor the response from Texas`() {
		val logger = LoggerFactory.getLogger(TexasRetry::class.java) as Logger
		val appender = ListAppender<ILoggingEvent>().apply { start() }
		logger.addAppender(appender)
		try {
			server.expect(times(2), requestTo(endpoint))
				.andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("feil med $token for 12345678901"))

			assertFailsWith<TokenServiceUnavailableException> { client.introspect(TokenIssuer.TOKENX, token) }

			assertEquals(1, appender.list.size)
			appender.list.forEach {
				assertFalse(it.formattedMessage.contains(token))
				assertFalse(it.formattedMessage.contains("12345678901"))
				assertNull(it.throwableProxy)
			}
		} finally {
			logger.detachAppender(appender)
		}
	}

	@Test
	fun `toString does not show claims`() {
		assertFalse(IntrospectionResult(true, mapOf("pid" to "12345678901")).toString().contains("12345678901"))
	}
}
