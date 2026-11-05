package no.nav.soknad.innsending.config

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.saf.SafClient
import no.nav.soknad.innsending.consumerapis.token.AccessTokenProvider
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.util.Constants
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Maskinklientene som henter token fra Texas: riktig target per integrasjon, tokenet sendes som bearer,
 * og feil mot Texas stopper kallet før det går til nedstrøms tjeneste.
 */
class MachineTokenClientsTest {

	private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
	private val accessTokenProvider = mockk<AccessTokenProvider>()
	private val config = RestClientOAuthConfig("innsending-api")

	@BeforeEach
	fun setUp() {
		wireMockServer.start()
		wireMockServer.stubFor(any(anyUrl()).willReturn(ok()))
	}

	@AfterEach
	fun tearDown() {
		wireMockServer.stop()
	}

	@Test
	fun `soknadsmottaker uses its own target and sends the token as bearer`() {
		val target = "api://dev-gcp.team-soknad.soknadsmottaker/.default"
		every { accessTokenProvider.getMachineToken(target) } returns "soknadsmottaker-token"

		val client = config.soknadsmottakerRestClient(restConfig(), accessTokenProvider, target)
		client.get().uri("/ping").retrieve().toBodilessEntity()

		verify(exactly = 1) { accessTokenProvider.getMachineToken(target) }
		wireMockServer.verify(
			getRequestedFor(urlEqualTo("/ping"))
				.withHeader("Authorization", equalTo("Bearer soknadsmottaker-token"))
				.withHeader(Constants.NAV_CONSUMER_ID, equalTo("innsending-api"))
				.withHeader(Constants.HEADER_CALL_ID, matching(".+"))
		)
	}

	@Test
	fun `a Texas failure stops the call to soknadsmottaker`() {
		every { accessTokenProvider.getMachineToken(any()) } throws TokenServiceUnavailableException("Texas svarte 503")

		val client = config.soknadsmottakerRestClient(restConfig(), accessTokenProvider, "target")

		assertFailsWith<TokenServiceUnavailableException> { client.get().uri("/ping").retrieve().toBodilessEntity() }
		wireMockServer.verify(0, anyRequestedFor(anyUrl()))
	}

	@Test
	fun `kodeverk uses its own target, sends the token as bearer and keeps Nav-Personident`() {
		val target = "api://dev-gcp.team-rocket.kodeverk-api/.default"
		every { accessTokenProvider.getMachineToken(target) } returns "kodeverk-token"
		val subjectHandler = mockk<SubjectHandlerInterface>()
		every { subjectHandler.getUserIdFromToken() } returns "12345678901"

		val client = config.kodeverkApiClient(restConfig(), accessTokenProvider, target, subjectHandler)
		client.get().uri("/kodeverk").retrieve().toBodilessEntity()

		verify(exactly = 1) { accessTokenProvider.getMachineToken(target) }
		wireMockServer.verify(
			getRequestedFor(urlEqualTo("/kodeverk"))
				.withHeader("Authorization", equalTo("Bearer kodeverk-token"))
				.withHeader(Constants.NAV_PERSON_IDENT, equalTo("12345678901"))
				.withHeader(Constants.NAV_CONSUMER_ID, equalTo("innsending-api"))
		)
	}

	@Test
	fun `SAF uses its own target and sends the token as bearer`() {
		val target = "api://dev-fss.teamdokumenthandtering.saf-q1/.default"
		every { accessTokenProvider.getMachineToken(target) } returns "saf-token"
		wireMockServer.stubFor(
			post(urlEqualTo("/graphql"))
				.willReturn(okJson("""{"data":{"dokumentoversiktBruker":{"journalposter":[]}}}"""))
		)

		safClient(target).hentDokumentoversiktBruker("12345678901")

		verify(exactly = 1) { accessTokenProvider.getMachineToken(target) }
		wireMockServer.verify(
			postRequestedFor(urlEqualTo("/graphql"))
				.withHeader("Authorization", equalTo("Bearer saf-token"))
				.withHeader(Constants.NAV_CONSUMER_ID, equalTo("innsending-api"))
		)
	}

	@Test
	fun `a Texas failure stops the call to SAF`() {
		every { accessTokenProvider.getMachineToken(any()) } throws TokenServiceUnavailableException("Texas svarte 503")

		assertFailsWith<BackendErrorException> { safClient("target").hentDokumentoversiktBruker("12345678901") }
		wireMockServer.verify(0, anyRequestedFor(anyUrl()))
	}

	private fun safClient(target: String) = SafClient(
		SafClientConfig(restConfig(), accessTokenProvider, target, "innsending-api").safGraphQLWebClient()
	)

	private fun restConfig() = RestConfig().apply {
		soknadsMottakerHost = wireMockServer.baseUrl()
		kodeverkUrl = wireMockServer.baseUrl()
		safUrl = wireMockServer.baseUrl()
	}
}
