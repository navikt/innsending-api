package no.nav.soknad.innsending.consumerapis.kontoregister

import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import io.mockk.mockk
import no.nav.soknad.innsending.exceptions.NonCriticalException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.web.client.RestClient

class KontoregisterServiceTest {

	companion object {
		// Uses the same stubs as the application tests, from src/test/resources/mappings/kontoregisterBorger.json
		@JvmField
		@RegisterExtension
		val wireMock: WireMockExtension = WireMockExtension.newInstance()
			.options(wireMockConfig().dynamicPort().withRootDirectory("src/test/resources"))
			.build()
	}

	private lateinit var kontoregisterService: KontoregisterService

	@BeforeEach
	fun setup() {
		val restClient = RestClient.builder().baseUrl("${wireMock.baseUrl()}/kontoregister-api/api/borger").build()
		kontoregisterService = KontoregisterService(mockk(), restClient)
	}

	@Test
	fun `Should get correct kontonummer`() {
		val kontonummer = kontoregisterService.getKontonummer()

		assertEquals("8361347234732292", kontonummer)
	}

	@Test
	fun `Should return NonCriticalException on failure`() {
		wireMock.setScenarioState("kontoregister-borger", "failed")

		assertThrows<NonCriticalException> {
			kontoregisterService.getKontonummer()
		}
	}
}
