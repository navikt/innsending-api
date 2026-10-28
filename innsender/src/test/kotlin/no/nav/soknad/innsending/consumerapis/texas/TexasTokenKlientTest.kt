package no.nav.soknad.innsending.consumerapis.texas

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
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

class TexasTokenKlientTest {

	private val tokenEndpoint = "http://texas.test/api/v1/token"
	private val target = "api://dev-gcp.team-soknad.soknadsmottaker/.default"
	private val token = "eyJhbGciOiJSUzI1NiJ9.hemmelig-texas-token.signatur"

	private lateinit var server: MockRestServiceServer
	private lateinit var klient: TexasTokenKlient

	private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
	private val appender = ListAppender<ILoggingEvent>()

	@BeforeEach
	fun setUp() {
		val builder = RestClient.builder().baseUrl(tokenEndpoint)
		server = MockRestServiceServer.bindTo(builder).build()
		klient = TexasTokenKlient(builder.build())
		appender.start()
		rootLogger.addAppender(appender)
	}

	@AfterEach
	fun tearDown() {
		rootLogger.detachAppender(appender)
		appender.stop()
	}

	@Test
	fun `ber om maskintoken fra Entra med riktig target`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(content().json("""{"identity_provider":"entra_id","target":"$target"}""", true))
			.andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON))

		assertEquals(token, klient.hentMaskinToken(target))
		server.verify()
	}

	@ParameterizedTest
	@ValueSource(ints = [400, 401, 403, 500, 502, 503])
	fun `feilstatus fra Texas gir TexasUnavailableException uten token og uten body`(status: Int) {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(
				withStatus(HttpStatus.valueOf(status))
					.contentType(MediaType.APPLICATION_JSON)
					.body("""{"error":"invalid_request","access_token":"$token"}""")
			)

		val feil = assertFailsWith<TexasUnavailableException> { klient.hentMaskinToken(target) }

		assertEquals("Texas svarte $status for identity_provider=entra_id target=$target", feil.message)
		assertEquals(null, feil.cause)
		server.verify()
		assertTokenIkkeLogget()
	}

	@Test
	fun `nettverksfeil og timeout gir TexasUnavailableException uten nytt forsøk`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withException(SocketTimeoutException("Read timed out")))

		val feil = assertFailsWith<TexasUnavailableException> { klient.hentMaskinToken(target) }

		assertEquals(null, feil.cause)
		server.verify()
		assertTokenIkkeLogget()
	}

	@Test
	fun `ugyldig JSON fra Texas gir TexasUnavailableException uten body`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess("""{"access_token":"$token" ikke json""", MediaType.APPLICATION_JSON))

		val feil = assertFailsWith<TexasUnavailableException> { klient.hentMaskinToken(target) }

		assertFalse(feil.message!!.contains(token))
		assertEquals(null, feil.cause)
		server.verify()
		assertTokenIkkeLogget()
	}

	@ParameterizedTest
	@ValueSource(strings = ["""{"expires_in":3599}""", """{"access_token":""}""", """{"access_token":42}"""])
	fun `svar uten gyldig access_token gir TexasUnavailableException`(body: String) {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess(body, MediaType.APPLICATION_JSON))

		assertFailsWith<TexasUnavailableException> { klient.hentMaskinToken(target) }
		server.verify()
	}

	@Test
	fun `tokenet logges ikke ved vellykket kall`() {
		server.expect(once(), requestTo(tokenEndpoint))
			.andRespond(withSuccess(tokenResponse(), MediaType.APPLICATION_JSON))

		klient.hentMaskinToken(target)

		assertTokenIkkeLogget()
	}

	private fun tokenResponse() = """{"access_token":"$token","expires_in":3599,"token_type":"Bearer"}"""

	private fun assertTokenIkkeLogget() {
		appender.list.forEach { event ->
			val tekst = event.formattedMessage + (event.throwableProxy?.let { ThrowableProxyUtil.asString(it) } ?: "")
			assertFalse(tekst.contains(token), "Tokenet ble logget: ${event.loggerName}")
		}
	}
}
