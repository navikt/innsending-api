package no.nav.soknad.innsending.consumerapis.texas

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.encoder.LogstashEncoder
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
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.ExpectedCount.times
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TexasTokenClientTest {

	private val tokenEndpoint = "http://texas.test/api/v1/token"
	private val exchangeEndpoint = "http://texas.test/api/v1/token/exchange"
	private val tokenxTarget = "dev-fss:pdl:pdl-api"
	private val userToken = "eyJhbGciOiJSUzI1NiJ9.innbyggerens-token-med-pid.signatur"
	private val target = "api://dev-gcp.team-soknad.soknadsmottaker/.default"
	private val token = "eyJhbGciOiJSUzI1NiJ9.hemmelig-texas-token.signatur"
	private val privateMarkers = listOf("synthetic-raw-message", "synthetic-body", "synthetic-cause", "synthetic-suppressed", "synthetic-header", "synthetic-person-data")

	private lateinit var server: MockRestServiceServer
	private lateinit var client: TexasTokenClient

	private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
	private val appender = ListAppender<ILoggingEvent>()

	@BeforeEach
	fun setUp() {
		val builder = RestClient.builder()
		server = MockRestServiceServer.bindTo(builder).build()
		client = TexasTokenClient(builder.build(), tokenEndpoint, exchangeEndpoint, retryWait = Duration.ZERO)
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
		assertEquals(0, diagnosticEvents().size)
	}

	@ParameterizedTest
	@ValueSource(ints = [400, 401, 403, 500])
	fun `an error status from Texas gives TokenServiceUnavailableException without retry, token or body`(status: Int) {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(
				withStatus(HttpStatus.valueOf(status))
					.contentType(MediaType.APPLICATION_JSON)
					.header("X-Private", privateMarkers[4])
					.body("""{"error":"${privateMarkers.joinToString()}","access_token":"$token"}""")
			)

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }

		assertEquals("Texas svarte $status for identity_provider=entra_id target=$target", error.message)
		assertEquals(null, error.cause)
		assertDiagnostic(status, error)
		server.verify()
		assertTokenNotLogged(error)
	}

	@ParameterizedTest
	@ValueSource(ints = [502, 503, 504])
	fun `a transient error status from Texas is retried once and then gives TokenServiceUnavailableException`(status: Int) {
		server.expect(times(2), requestTo(tokenEndpoint))
			.andRespond(
				withStatus(HttpStatus.valueOf(status))
					.contentType(MediaType.APPLICATION_JSON)
					.body("""{"error":"upstream","access_token":"$token"}""")
			)

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }

		assertEquals("Texas svarte $status for identity_provider=entra_id target=$target", error.message)
		assertEquals(null, error.cause)
		assertDiagnostic(status, error)
		assertEquals(1, appender.list.count { it.loggerName == TexasRetry::class.java.name })
		server.verify()
		assertTokenNotLogged()
	}

	@Test
	fun `network errors and timeouts are retried once and then give TokenServiceUnavailableException`() {
		server.expect(times(2), requestTo(tokenEndpoint))
			.andRespond(withException(SocketTimeoutException("Read timed out")))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }

		assertEquals(null, error.cause)
		val diagnostic = assertDiagnostic(null, error)
		assertTrue(diagnostic["exception_types"].any { it.asText() == SocketTimeoutException::class.java.name })
		server.verify()
		assertTokenNotLogged()
	}

	@Test
	fun `a machine token is returned when the retry after a network error succeeds`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withException(SocketTimeoutException("Read timed out")))
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON))

		assertEquals(token, client.getMachineToken(target))
		server.verify()
		assertTokenNotLogged()
		assertEquals(0, diagnosticEvents().size)
	}

	@ParameterizedTest
	@ValueSource(strings = ["application/json", "text/plain"])
	fun `invalid JSON or content type gives safe parsing diagnostics`(contentType: String) {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess("""{"access_token":"$token","private":"${privateMarkers.joinToString()}" ikke json""", MediaType.parseMediaType(contentType)))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }

		assertFalse(error.message!!.contains(token))
		assertEquals(null, error.cause)
		val diagnostic = assertDiagnostic(200, error)
		assertTrue(diagnostic["exception_types"].any {
			if (contentType == "text/plain") it.asText() == "org.springframework.web.client.UnknownContentTypeException"
			else it.asText() == "tools.jackson.core.exc.StreamReadException"
		})
		server.verify()
		assertTokenNotLogged(error)
	}

	@ParameterizedTest
	@ValueSource(strings = ["""{"expires_in":3599}""", """{"access_token":""}""", """{"access_token":42}"""])
	fun `a response without a valid access_token gives TokenServiceUnavailableException`(body: String) {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }
		assertEquals(0, assertDiagnostic(200, error)["exception_types"].size())
		assertEquals("Svaret fra Texas manglet access_token for identity_provider=entra_id target=$target", error.message)
		assertTokenNotLogged(error)
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
	@ValueSource(ints = [400, 401, 500, 503, 504])
	fun `an exchange failure gives TokenServiceUnavailableException without the citizen token`(status: Int) {
		server.expect(if (status >= 502) times(2) else once(), requestTo(exchangeEndpoint))
			.andRespond(
				withStatus(HttpStatus.valueOf(status))
					.contentType(MediaType.APPLICATION_JSON)
					.body("""{"error":"invalid_grant","user_token":"$userToken"}""")
			)

		val error = assertFailsWith<TokenServiceUnavailableException> { client.exchangeUserToken(tokenxTarget, userToken) }

		assertEquals("Texas svarte $status for identity_provider=tokenx target=$tokenxTarget", error.message)
		assertEquals(null, error.cause)
		assertDiagnostic(status, error, "exchange")
		server.verify()
		assertTokenNotLogged()
	}

	@Test
	fun `network errors during exchange are retried once and then give TokenServiceUnavailableException`() {
		server.expect(times(2), requestTo(exchangeEndpoint))
			.andRespond(withException(SocketTimeoutException("Read timed out")))

		val error = assertFailsWith<TokenServiceUnavailableException> { client.exchangeUserToken(tokenxTarget, userToken) }

		assertFalse(error.message!!.contains(userToken))
		assertDiagnostic(null, error, "exchange")
		server.verify()
		assertTokenNotLogged()
	}

	@ParameterizedTest
	@ValueSource(ints = [0, 32])
	fun `a response read failure keeps its type without private content`(bytesBeforeFailure: Int) {
		val readError = IOException("${privateMarkers.joinToString()} $token $userToken")
		server.expect(once(), requestTo(tokenEndpoint)).andRespond {
			MockClientHttpResponse(object : InputStream() {
				private val prefix = """{"access_token":"${"x".repeat(32)}""".toByteArray()
				private var position = 0
				override fun read(): Int = if (position < bytesBeforeFailure) prefix[position++].toInt() else throw readError
			}, HttpStatus.OK).apply { headers.contentType = MediaType.APPLICATION_JSON }
		}

		val error = assertFailsWith<TokenServiceUnavailableException> { client.getMachineToken(target) }

		val diagnostic = assertDiagnostic(200, error)
		assertTrue(diagnostic["exception_types"].any { it.asText() == IOException::class.java.name })
		assertTokenNotLogged(error)
		server.verify()
	}

	@ParameterizedTest
	@ValueSource(ints = [2, 12])
	fun `nested unknown causes are bounded and never retain private messages or suppressed errors`(depth: Int) {
		val causes = List(depth) { IllegalStateException("${privateMarkers.joinToString()} $token $userToken") }
		causes.zipWithNext().forEach { (outer, inner) -> outer.initCause(inner) }
		if (depth == 2) causes.last().initCause(causes.first())
		val original = RestClientException(privateMarkers[0], causes.first()).apply {
			addSuppressed(IllegalArgumentException(privateMarkers[3]))
		}
		server.expect(once(), requestTo(exchangeEndpoint)).andRespond { throw original }

		val error = assertFailsWith<TokenServiceUnavailableException> { client.exchangeUserToken(tokenxTarget, userToken) }

		val types = assertDiagnostic(null, error, "exchange")["exception_types"].map { it.asText() }
		assertEquals(listOf(RestClientException::class.java.name) + List(3) { IllegalStateException::class.java.name }, types)
		assertEquals(null, error.cause)
		assertTrue(error.suppressed.isEmpty())
		assertTokenNotLogged(error)
		server.verify()
	}

	private fun diagnosticEvents() = appender.list.filter { it.loggerName == TexasTokenClient::class.java.name }

	private fun assertDiagnostic(status: Int?, error: TokenServiceUnavailableException, operation: String = "token"): JsonNode {
		val event = diagnosticEvents().single()
		assertEquals(Level.WARN, event.level)
		assertEquals(null, event.throwableProxy)
		val diagnostic = jacksonObjectMapper().readTree(encode(event))
		assertEquals(status, diagnostic["http_status"]?.takeUnless { it.isNull }?.asInt())
		assertEquals(operation, diagnostic["operation"].asText())
		assertEquals(if (operation == "token") "entra_id" else "tokenx", diagnostic["identity_provider"].asText())
		assertEquals(if (operation == "token") target else tokenxTarget, diagnostic["target"].asText())
		assertEquals(error.message, diagnostic["reason"].asText())
		return diagnostic
	}

	private fun tokenResponse() = """{"access_token":"$token","expires_in":3599,"token_type":"Bearer"}"""

	private fun encode(event: ILoggingEvent): String {
		val encoder = LogstashEncoder().apply {
			context = rootLogger.loggerContext
			start()
		}
		return try {
			encoder.encode(event).toString(Charsets.UTF_8)
		} finally {
			encoder.stop()
		}
	}

	private fun assertTokenNotLogged(error: Throwable? = null) {
		val output = appender.list.joinToString("\n") { event ->
			event.formattedMessage + (event.throwableProxy?.let { ThrowableProxyUtil.asString(it) } ?: "") + encode(event)
		} + error?.stackTraceToString().orEmpty()
		(listOf(token, userToken) + privateMarkers).forEach { marker ->
			assertFalse(output.contains(marker), "Privat testmarkør finnes i logg eller videreført feil")
		}
	}
}
