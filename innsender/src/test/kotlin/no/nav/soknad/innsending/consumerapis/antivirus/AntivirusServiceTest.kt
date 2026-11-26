package no.nav.soknad.innsending.consumerapis.antivirus

import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals

class AntivirusServiceTest {

	companion object {
		// Uses the same stubs as the application tests, from src/test/resources/mappings/antivirusStub.json
		@JvmField
		@RegisterExtension
		val wireMock: WireMockExtension = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort().withRootDirectory("src/test/resources"))
			.build()
	}

	private lateinit var antivirusService: AntivirusService

	@BeforeEach
	fun setup() {
		val restClient = RestClient.builder().baseUrl("${wireMock.baseUrl()}/antivirus").build()
		antivirusService = AntivirusService(restClient, InnsenderMetrics(PrometheusRegistry()))
	}

	@Test
	fun `Skal gi true naar virusscan returnerer OK`() {
		val result = antivirusService.scan("testfil".toByteArray())

		assertEquals(AntivirusScanResult.OK, result)
	}

	@Test
	fun `Skal gi false naar virusscan returnerer FOUND`() {
		wireMock.setScenarioState("antivirus", "virus-found")

		val result = antivirusService.scan("testfil".toByteArray())

		assertEquals(AntivirusScanResult.FOUND, result)
	}

	@Test
	fun `Skal gi false naar virusscan returnerer ERROR`() {
		wireMock.setScenarioState("antivirus", "virus-error")

		val result = antivirusService.scan("testfil".toByteArray())

		assertEquals(AntivirusScanResult.ERROR, result)
	}
}
