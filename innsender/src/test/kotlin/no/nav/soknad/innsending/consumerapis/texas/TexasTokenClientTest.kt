package no.nav.soknad.innsending.consumerapis.texas

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.once
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

class TexasTokenClientTest {

	private val tokenEndpoint = "http://texas.test/api/v1/token"
	private val exchangeEndpoint = "http://texas.test/api/v1/token/exchange"
	private val tokenxTarget = "dev-fss:pdl:pdl-api"
	private val userToken = "eyJhbGciOiJSUzI1NiJ9.innbyggerens-token-med-pid.signatur"
	private val target = "api://dev-gcp.team-soknad.soknadsmottaker/.default"
	private val token = "eyJhbGciOiJSUzI1NiJ9.hemmelig-texas-token.signatur"

	private lateinit var server: MockRestServiceServer
	private lateinit var client: TexasTokenClient

	private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
	private val appender = ListAppender<ILoggingEvent>()

	@BeforeEach
	fun setUp() {
		val builder = RestClient.builder()
		server = MockRestServiceServer.bindTo(builder).build()
		client = TexasTokenClient(builder.build(), tokenEndpoint, exchangeEndpoint)
		appender.start()
		rootLogger.addAppender(appender)
	}

	@AfterEach
	fun tearDown() {
		rootLogger.detachAppender(appender)
		appender.stop()
	}

	@Test
	fun `requests a machine token from Entra with the right target`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("""{"identity_provider":"entra_id","target":"$target"}""", true))
			.andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON))

		assertEquals(token, client.getMachineToken(target))
		server.verify()
	}

	@ParameterizedTest
	@ValueSource(ints = [400, 401, 403, 500, 502, 503])
	fun `an error status from Texas gives TokenServiceUnavailableException without token and without body`(status: Int) {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(
				withStatus(HttpStatus.valueOf(status))
					.contentType(MediaType.APPLICATION_JSON)
					.body("""{"error":"invalid_request","access_token":"$token"}""")
			)

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }

		assertEquals("Texas svarte $status for identity_provider=entra_id target=$target", error.message)
		assertEquals(null, error.cause)
		server.verify()
		assertTokenNotLogged()
	}

	@Test
	fun `network errors and timeouts give TokenServiceUnavailableException without retry`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withException(SocketTimeoutException("Read timed out")))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }

		assertEquals(null, error.cause)
		server.verify()
		assertTokenNotLogged()
	}

	@Test
	fun `invalid JSON from Texas gives TokenServiceUnavailableException without body`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess("""{"access_token":"$token" ikke json""", MediaType.APPLICATION_JSON))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }

		assertFalse(error.message!!.contains(token))
		assertEquals(null, error.cause)
		server.verify()
		assertTokenNotLogged()
	}

	@ParameterizedTest
	@ValueSource(strings = ["""{"expires_in":3599}""", """{"access_token":""}""", """{"access_token":42}"""])
	fun `a response without a valid access_token gives TokenServiceUnavailableException`(body: String) {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

		assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }
		server.verify()
	}

	@Test
	fun `the token is not logged on a successful call`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON))

		client.getMachineToken(target)

		assertTokenNotLogged()
	}

	@Test
	fun `exchanges the citizen token at TokenX with the right target`() {
		server.expect(once(), requestTo(exchangeEndpoint))
			.andExpect(method(HttpMethod.POST))
			.andExpect(
				content().json(
					"""{"identity_provider":"tokenx","target":"$tokenxTarget","user_token":"$userToken"}""", true
				)
			)
			.andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON))

		assertEquals(token, client.exchangeUserToken(tokenxTarget, userToken))
		server.verify()
		assertTokenNotLogged()
	}

	@ParameterizedTest
	@ValueSource(ints = [400, 401, 500, 503])
	fun `an exchange failure gives TokenServiceUnavailableException without the citizen token`(status: Int) {
		server.expect(once(), requestTo(exchangeEndpoint))
			.andRespond(
				withStatus(HttpStatus.valueOf(status))
					.contentType(MediaType.APPLICATION_JSON)
					.body("""{"error":"invalid_grant","user_token":"$userToken"}""")
			)

		val error = assertFailsWith<TokenServiceUnavailableException> { client.exchangeUserToken(tokenxTarget, userToken) }

		assertEquals("Texas svarte $status for identity_provider=tokenx target=$tokenxTarget", error.message)
		assertEquals(null, error.cause)
		server.verify()
		assertTokenNotLogged()
	}

	@Test
	fun `network errors during exchange give TokenServiceUnavailableException without retry`() {
		server.expect(once(), requestTo(exchangeEndpoint))
			.andRespond(withException(SocketTimeoutException("Read timed out")))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.exchangeUserToken(tokenxTarget, userToken) }

		assertFalse(error.message!!.contains(userToken))
		server.verify()
		assertTokenNotLogged()
	}

	private fun tokenResponse() = """{"access_token":"$token","expires_in":3599,"token_type":"Bearer"}"""

	private fun assertTokenNotLogged() {
		appender.list.forEach { event ->
			val text = event.formattedMessage + (event.throwableProxy?.let { ThrowableProxyUtil.asString(it) } ?: "")
			assertFalse(text.contains(token), "Tokenet ble logget: ${event.loggerName}")
			assertFalse(text.contains(userToken), "Innbyggerens token ble logget: ${event.loggerName}")
		}
	}
}
