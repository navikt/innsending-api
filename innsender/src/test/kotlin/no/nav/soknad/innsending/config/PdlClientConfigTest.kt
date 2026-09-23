package no.nav.soknad.innsending.config

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.pdl.PdlAPI
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

	private fun createClientConfig(): PdlClientConfig {
		val restConfig = RestConfig().apply { pdlUrl = wireMockServer.baseUrl() }
		val accessToken: AccessToken = mockk()
		every { accessToken.getAccessToken(any(), any()) } returns "token"
		return PdlClientConfig(restConfig, accessToken)
	}
}
