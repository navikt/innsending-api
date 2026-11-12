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
import no.nav.soknad.innsending.consumerapis.token.AccessTokenProvider
import no.nav.soknad.innsending.consumerapis.token.TokenServiceUnavailableException
import no.nav.soknad.innsending.security.UserTokenSource
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
	fun `exchanges the citizen TokenX token with PDL as target`() {
		wireMockServer.start()
		wireMockServer.stubFor(post(urlEqualTo("/graphql")).willReturn(okJson(identerResponse())))

		PdlAPI(createClientConfig().graphQLClient()).hentPersonIdents("12345678901")

		verify(exactly = 1) { accessTokenProvider.exchangeUserToken(pdlTarget, "innbyggerens-tokenx-token") }
		wireMockServer.verify(
			postRequestedFor(urlEqualTo("/graphql"))
				.withHeader("Authorization", equalTo("Bearer pdl-token"))
				.withHeader(HEADER_BEHANDLINGSNUMMER, equalTo(PDL_BEHANDLINGSNUMMER))
		)
	}

	@Test
	fun `a Texas failure stops the call to PDL with the same fallback as before`() {
		wireMockServer.start()
		every { accessTokenProvider.exchangeUserToken(any(), any()) } throws TokenServiceUnavailableException("Texas svarte 503")

		val idents = PdlAPI(createClientConfig().graphQLClient()).hentPersonIdents("12345678901")

		assertEquals(listOf("12345678901"), idents.map { it.ident })
		wireMockServer.verify(0, anyRequestedFor(anyUrl()))
	}

	@Test
	fun `without a TokenX token neither Texas nor PDL is called, and the fallback is unchanged`() {
		wireMockServer.start()
		every { userTokenSource.getUserToken() } returns null

		val idents = PdlAPI(createClientConfig().graphQLClient()).hentPersonIdents("12345678901")

		assertEquals(listOf("12345678901"), idents.map { it.ident })
		verify(exactly = 0) { accessTokenProvider.exchangeUserToken(any(), any()) }
		wireMockServer.verify(0, anyRequestedFor(anyUrl()))
	}

	private val pdlTarget = "dev-fss:pdl:pdl-api"
	private val accessTokenProvider = mockk<AccessTokenProvider> {
		every { exchangeUserToken(pdlTarget, "innbyggerens-tokenx-token") } returns "pdl-token"
	}
	private val userTokenSource = mockk<UserTokenSource> {
		every { getUserToken() } returns "innbyggerens-tokenx-token"
	}

	private fun identerResponse() =
		"""{"data":{"hentIdenter":{"identer":[{"ident":"12345678901","gruppe":"FOLKEREGISTERIDENT","historisk":false}]}}}"""

	private fun createClientConfig(): PdlClientConfig {
		val restConfig = RestConfig().apply { pdlUrl = wireMockServer.baseUrl() }
		return PdlClientConfig(restConfig, accessTokenProvider, userTokenSource, pdlTarget)
	}
}
