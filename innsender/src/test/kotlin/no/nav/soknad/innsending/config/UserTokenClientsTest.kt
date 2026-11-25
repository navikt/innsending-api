package no.nav.soknad.innsending.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.token.AccessTokenProvider
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.security.UserTokenSource
import no.nav.soknad.innsending.security.MissingUserTokenException
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Constants
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Klientene som kaller nedstrøms på vegne av innbyggeren: innbyggerens TokenX-token byttes hos Texas med riktig target,
 * det byttede tokenet sendes som bearer, og feil mot Texas eller manglende TokenX-token stopper kallet før det går nedstrøms.
 *
 * Klienten fra konfigurasjonen kobles til [MockRestServiceServer] med `mutate()`, slik at interceptoren er med
 * uten at testen åpner porter.
 */
class UserTokenClientsTest {

	private val userToken = "eyJhbGciOiJSUzI1NiJ9.innbyggerens-tokenx-token.signatur"
	private val exchangedToken = "eyJhbGciOiJSUzI1NiJ9.byttet-token-fra-texas.signatur"
	private val fnr = "12345678901"

	private val accessTokenProvider = mockk<AccessTokenProvider>()
	private val userTokenSource = mockk<UserTokenSource>()
	private val subjectHandler = mockk<SubjectHandlerInterface>()
	private val config = RestClientOAuthConfig("innsending-api")

	private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
	private val appender = ListAppender<ILoggingEvent>()

	@BeforeEach
	fun setUp() {
		every { subjectHandler.getUserIdFromToken() } returns fnr
		appender.start()
		rootLogger.addAppender(appender)
	}

	@AfterEach
	fun tearDown() {
		rootLogger.detachAppender(appender)
		appender.stop()
	}

	@Test
	fun `arena exchanges the citizen token for the arena target and sends the exchanged token and Nav-Personident`() {
		val target = "dev-fss:teamarenanais:arena-api-q2"
		every { userTokenSource.getUserToken() } returns userToken
		every { accessTokenProvider.exchangeUserToken(target, userToken) } returns exchangedToken

		val (client, server) = withMockServer(arenaClient(target))
		server.expect(once(), requestTo("$ARENA_URL/maalgrupper"))
			.andExpect(header("Authorization", "Bearer $exchangedToken"))
			.andExpect(header(Constants.NAV_PERSON_IDENT, fnr))
			.andExpect(header(Constants.NAV_CONSUMER_ID, "innsending-api"))
			.andRespond(withSuccess())

		client.get().uri("/maalgrupper").retrieve().toBodilessEntity()

		server.verify()
		verify(exactly = 1) { accessTokenProvider.exchangeUserToken(target, userToken) }
		assertTokenNotLogged()
	}

	@Test
	fun `a Texas failure stops the call to arena`() {
		every { userTokenSource.getUserToken() } returns userToken
		every { accessTokenProvider.exchangeUserToken(any(), any()) } throws TokenServiceUnavailableException("Texas svarte 503")

		val (client, server) = withMockServer(arenaClient("target"))

		assertFailsWith<TokenServiceUnavailableException> { client.get().uri("/maalgrupper").retrieve().toBodilessEntity() }
		server.verify()
		assertTokenNotLogged()
	}

	@Test
	fun `arena is not called without a TokenX token`() {
		every { userTokenSource.getUserToken() } returns null

		val (client, server) = withMockServer(arenaClient("target"))

		assertFailsWith<MissingUserTokenException> { client.get().uri("/maalgrupper").retrieve().toBodilessEntity() }
		server.verify()
		verify(exactly = 0) { accessTokenProvider.exchangeUserToken(any(), any()) }
	}

	@Test
	fun `kontoregister exchanges the citizen token for the kontoregister target and sends the exchanged token`() {
		val target = "dev-gcp:okonomi:sokos-kontoregister-person"
		every { userTokenSource.getUserToken() } returns userToken
		every { accessTokenProvider.exchangeUserToken(target, userToken) } returns exchangedToken

		val (client, server) = withMockServer(kontoregisterClient(target))
		server.expect(once(), requestTo("$KONTOREGISTER_URL/api/borger/kontooppslag"))
			.andExpect(header("Authorization", "Bearer $exchangedToken"))
			.andExpect(header(Constants.NAV_CONSUMER_ID, "innsending-api"))
			.andExpect(headerDoesNotExist(Constants.NAV_PERSON_IDENT))
			.andRespond(withSuccess())

		client.get().uri("/kontooppslag").retrieve().toBodilessEntity()

		server.verify()
		verify(exactly = 1) { accessTokenProvider.exchangeUserToken(target, userToken) }
		assertTokenNotLogged()
	}

	@Test
	fun `a Texas failure stops the call to kontoregister`() {
		every { userTokenSource.getUserToken() } returns userToken
		every { accessTokenProvider.exchangeUserToken(any(), any()) } throws TokenServiceUnavailableException("Texas svarte 503")

		val (client, server) = withMockServer(kontoregisterClient("target"))

		assertFailsWith<TokenServiceUnavailableException> { client.get().uri("/kontooppslag").retrieve().toBodilessEntity() }
		server.verify()
		assertTokenNotLogged()
	}

	@Test
	fun `kontoregister is not called without a TokenX token`() {
		every { userTokenSource.getUserToken() } returns null

		val (client, server) = withMockServer(kontoregisterClient("target"))

		assertFailsWith<MissingUserTokenException> { client.get().uri("/kontooppslag").retrieve().toBodilessEntity() }
		server.verify()
		verify(exactly = 0) { accessTokenProvider.exchangeUserToken(any(), any()) }
	}

	private fun arenaClient(target: String) =
		config.arenaApiClient(restConfig(), accessTokenProvider, userTokenSource, target, subjectHandler)

	private fun kontoregisterClient(target: String) =
		config.kontoregisterApiClient(restConfig(), accessTokenProvider, userTokenSource, target)

	/** Uten forventninger svarer serveren med AssertionError på alle kall, så et kall nedstrøms ville gitt feil unntak. */
	private fun withMockServer(client: RestClient): Pair<RestClient, MockRestServiceServer> {
		val builder = client.mutate()
		val server = MockRestServiceServer.bindTo(builder).build()
		return builder.build() to server
	}

	private fun restConfig() = RestConfig().apply {
		arenaUrl = ARENA_URL
		kontoregisterUrl = KONTOREGISTER_URL
	}

	private fun assertTokenNotLogged() {
		appender.list.forEach { event ->
			val text = event.formattedMessage + (event.throwableProxy?.let { ThrowableProxyUtil.asString(it) } ?: "")
			assertFalse(text.contains(userToken), "Innbyggerens token ble logget: ${event.loggerName}")
			assertFalse(text.contains(exchangedToken), "Byttet token ble logget: ${event.loggerName}")
		}
	}

	private companion object {
		const val ARENA_URL = "http://arena.test"
		const val KONTOREGISTER_URL = "http://kontoregister.test"
	}
}
