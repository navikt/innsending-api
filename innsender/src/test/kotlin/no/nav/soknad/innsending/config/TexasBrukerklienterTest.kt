package no.nav.soknad.innsending.config

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.texas.TexasTokenKlient
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.security.BrukerTokenKilde
import no.nav.soknad.innsending.security.ManglerBrukerTokenException
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
class TexasBrukerklienterTest {

	private val brukerToken = "eyJhbGciOiJSUzI1NiJ9.innbyggerens-tokenx-token.signatur"
	private val byttetToken = "eyJhbGciOiJSUzI1NiJ9.byttet-token-fra-texas.signatur"
	private val fnr = "12345678901"

	private val texasTokenKlient = mockk<TexasTokenKlient>()
	private val brukerTokenKilde = mockk<BrukerTokenKilde>()
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
	fun `arena bytter innbyggerens token med arena-target og sender byttet token og Nav-Personident`() {
		val target = "dev-fss:teamarenanais:arena-api-q2"
		every { brukerTokenKilde.hentTokenXToken() } returns brukerToken
		every { texasTokenKlient.hentBrukerToken(target, brukerToken) } returns byttetToken

		val (client, server) = medMockServer(arenaClient(target))
		server.expect(once(), requestTo("$ARENA_URL/maalgrupper"))
			.andExpect(header("Authorization", "Bearer $byttetToken"))
			.andExpect(header(Constants.NAV_PERSON_IDENT, fnr))
			.andExpect(header(Constants.NAV_CONSUMER_ID, "innsending-api"))
			.andRespond(withSuccess())

		client.get().uri("/maalgrupper").retrieve().toBodilessEntity()

		server.verify()
		verify(exactly = 1) { texasTokenKlient.hentBrukerToken(target, brukerToken) }
		assertTokenIkkeLogget()
	}

	@Test
	fun `feil mot Texas stopper kallet til arena`() {
		every { brukerTokenKilde.hentTokenXToken() } returns brukerToken
		every { texasTokenKlient.hentBrukerToken(any(), any()) } throws TexasUnavailableException("Texas svarte 503")

		val (client, server) = medMockServer(arenaClient("target"))

		assertFailsWith<TexasUnavailableException> { client.get().uri("/maalgrupper").retrieve().toBodilessEntity() }
		server.verify()
		assertTokenIkkeLogget()
	}

	@Test
	fun `arena kalles ikke uten TokenX-token`() {
		every { brukerTokenKilde.hentTokenXToken() } returns null

		val (client, server) = medMockServer(arenaClient("target"))

		assertFailsWith<ManglerBrukerTokenException> { client.get().uri("/maalgrupper").retrieve().toBodilessEntity() }
		server.verify()
		verify(exactly = 0) { texasTokenKlient.hentBrukerToken(any(), any()) }
	}

	@Test
	fun `kontoregister bytter innbyggerens token med kontoregister-target og sender byttet token`() {
		val target = "dev-gcp:okonomi:sokos-kontoregister-person"
		every { brukerTokenKilde.hentTokenXToken() } returns brukerToken
		every { texasTokenKlient.hentBrukerToken(target, brukerToken) } returns byttetToken

		val (client, server) = medMockServer(kontoregisterClient(target))
		server.expect(once(), requestTo("$KONTOREGISTER_URL/api/borger/kontooppslag"))
			.andExpect(header("Authorization", "Bearer $byttetToken"))
			.andExpect(header(Constants.NAV_CONSUMER_ID, "innsending-api"))
			.andExpect(headerDoesNotExist(Constants.NAV_PERSON_IDENT))
			.andRespond(withSuccess())

		client.get().uri("/kontooppslag").retrieve().toBodilessEntity()

		server.verify()
		verify(exactly = 1) { texasTokenKlient.hentBrukerToken(target, brukerToken) }
		assertTokenIkkeLogget()
	}

	@Test
	fun `feil mot Texas stopper kallet til kontoregister`() {
		every { brukerTokenKilde.hentTokenXToken() } returns brukerToken
		every { texasTokenKlient.hentBrukerToken(any(), any()) } throws TexasUnavailableException("Texas svarte 503")

		val (client, server) = medMockServer(kontoregisterClient("target"))

		assertFailsWith<TexasUnavailableException> { client.get().uri("/kontooppslag").retrieve().toBodilessEntity() }
		server.verify()
		assertTokenIkkeLogget()
	}

	@Test
	fun `kontoregister kalles ikke uten TokenX-token`() {
		every { brukerTokenKilde.hentTokenXToken() } returns null

		val (client, server) = medMockServer(kontoregisterClient("target"))

		assertFailsWith<ManglerBrukerTokenException> { client.get().uri("/kontooppslag").retrieve().toBodilessEntity() }
		server.verify()
		verify(exactly = 0) { texasTokenKlient.hentBrukerToken(any(), any()) }
	}

	private fun arenaClient(target: String) =
		config.arenaApiClient(restConfig(), texasTokenKlient, brukerTokenKilde, target, subjectHandler)

	private fun kontoregisterClient(target: String) =
		config.kontoregisterApiClient(restConfig(), texasTokenKlient, brukerTokenKilde, target)

	/** Uten forventninger svarer serveren med AssertionError på alle kall, så et kall nedstrøms ville gitt feil unntak. */
	private fun medMockServer(client: RestClient): Pair<RestClient, MockRestServiceServer> {
		val builder = client.mutate()
		val server = MockRestServiceServer.bindTo(builder).build()
		return builder.build() to server
	}

	private fun restConfig() = RestConfig().apply {
		arenaUrl = ARENA_URL
		kontoregisterUrl = KONTOREGISTER_URL
	}

	private fun assertTokenIkkeLogget() {
		appender.list.forEach { event ->
			val tekst = event.formattedMessage + (event.throwableProxy?.let { ThrowableProxyUtil.asString(it) } ?: "")
			assertFalse(tekst.contains(brukerToken), "Innbyggerens token ble logget: ${event.loggerName}")
			assertFalse(tekst.contains(byttetToken), "Byttet token ble logget: ${event.loggerName}")
		}
	}

	private companion object {
		const val ARENA_URL = "http://arena.test"
		const val KONTOREGISTER_URL = "http://kontoregister.test"
	}
}
