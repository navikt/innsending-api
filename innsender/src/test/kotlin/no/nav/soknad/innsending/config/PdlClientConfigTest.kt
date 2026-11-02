package no.nav.soknad.innsending.config

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.pdl.PdlAPI
import no.nav.soknad.innsending.consumerapis.texas.TexasTokenKlient
import no.nav.soknad.innsending.consumerapis.texas.TexasUnavailableException
import no.nav.soknad.innsending.security.BrukerTokenKilde
import no.nav.soknad.innsending.util.Constants.HEADER_BEHANDLINGSNUMMER
import no.nav.soknad.innsending.util.Constants.PDL_BEHANDLINGSNUMMER
import no.nav.soknad.innsending.pdl.generated.HentIdenter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdlClientConfigTest {

	private val wireMockServer = WireMockServer(wireMockConfig().dynamicPort())

	@AfterEach
	fun tearDown() {
		wireMockServer.stop()
	}

	@Test
	fun `retries when PDL resets the connection`() {
		wireMockServer.start()
		wireMockServer.stubFor(
			post(urlEqualTo("/graphql"))
				.inScenario("connection reset")
				.whenScenarioStateIs(STARTED)
				.willSetStateTo("connection restored")
				.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
		)
		wireMockServer.stubFor(
			post(urlEqualTo("/graphql"))
				.inScenario("connection reset")
				.whenScenarioStateIs("connection restored")
				.willReturn(
					okJson(
						"""
						{
						  "data": {
						    "hentIdenter": {
						      "identer": [
						        {
						          "ident": "12345678901",
						          "gruppe": "FOLKEREGISTERIDENT",
						          "historisk": false
						        }
						      ]
						    }
						  }
						}
						""".trimIndent()
					)
				)
		)

		val response = runBlocking {
			createClientConfig().graphQLClient().execute(HentIdenter(HentIdenter.Variables("12345678901")))
		}

		assertEquals("12345678901", response.data?.hentIdenter?.identer?.single()?.ident)
		wireMockServer.verify(2, postRequestedFor(urlEqualTo("/graphql")))
	}

	@Test
	fun `falls back to the authenticated identity after transport retries are exhausted`() {
		wireMockServer.start()
		wireMockServer.stubFor(
			post(urlEqualTo("/graphql"))
				.willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
		)
		val pdlApi = PdlAPI(createClientConfig().graphQLClient())

		val idents = pdlApi.hentPersonIdents("12345678901")

		assertEquals(listOf("12345678901"), idents.map { it.ident })
		wireMockServer.verify(4, postRequestedFor(urlEqualTo("/graphql")))
	}

	@Test
	fun `bytter innbyggerens TokenX-token hos Texas med PDL som target`() {
		wireMockServer.start()
		wireMockServer.stubFor(post(urlEqualTo("/graphql")).willReturn(okJson(identerResponse())))

		PdlAPI(createClientConfig().graphQLClient()).hentPersonIdents("12345678901")

		verify(exactly = 1) { texasTokenKlient.hentBrukerToken(pdlTarget, "innbyggerens-tokenx-token") }
		wireMockServer.verify(
			postRequestedFor(urlEqualTo("/graphql"))
				.withHeader("Authorization", equalTo("Bearer pdl-token"))
				.withHeader(HEADER_BEHANDLINGSNUMMER, equalTo(PDL_BEHANDLINGSNUMMER))
		)
	}

	@Test
	fun `feil mot Texas stopper kallet til PDL og gir samme fallback som før`() {
		wireMockServer.start()
		every { texasTokenKlient.hentBrukerToken(any(), any()) } throws TexasUnavailableException("Texas svarte 503")

		val idents = PdlAPI(createClientConfig().graphQLClient()).hentPersonIdents("12345678901")

		assertEquals(listOf("12345678901"), idents.map { it.ident })
		wireMockServer.verify(0, anyRequestedFor(anyUrl()))
	}

	@Test
	fun `uten TokenX-token kalles verken Texas eller PDL, og fallback er som før`() {
		wireMockServer.start()
		every { brukerTokenKilde.hentTokenXToken() } returns null

		val idents = PdlAPI(createClientConfig().graphQLClient()).hentPersonIdents("12345678901")

		assertEquals(listOf("12345678901"), idents.map { it.ident })
		verify(exactly = 0) { texasTokenKlient.hentBrukerToken(any(), any()) }
		wireMockServer.verify(0, anyRequestedFor(anyUrl()))
	}

	private val pdlTarget = "dev-fss:pdl:pdl-api"
	private val texasTokenKlient = mockk<TexasTokenKlient> {
		every { hentBrukerToken(pdlTarget, "innbyggerens-tokenx-token") } returns "pdl-token"
	}
	private val brukerTokenKilde = mockk<BrukerTokenKilde> {
		every { hentTokenXToken() } returns "innbyggerens-tokenx-token"
	}

	private fun identerResponse() =
		"""{"data":{"hentIdenter":{"identer":[{"ident":"12345678901","gruppe":"FOLKEREGISTERIDENT","historisk":false}]}}}"""

	private fun createClientConfig(): PdlClientConfig {
		val restConfig = RestConfig().apply { pdlUrl = wireMockServer.baseUrl() }
		return PdlClientConfig(restConfig, texasTokenKlient, brukerTokenKilde, pdlTarget)
	}
}
