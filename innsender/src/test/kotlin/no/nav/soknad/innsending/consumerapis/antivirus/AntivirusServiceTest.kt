package no.nav.soknad.innsending.consumerapis.antivirus

import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals

class AntivirusServiceTest : ApplicationTest() {

	private lateinit var antivirusService: AntivirusService

	@Autowired
		private lateinit var antivirusRestClient: RestClient

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@BeforeEach
	fun setup() {
		WireMock.reset()
		antivirusService = AntivirusService(
			antivirusRestClient,
			innsenderMetrics,
		)
	}

	@AfterEach
	fun tearDown() {
		WireMock.reset()
	}

	@Test
	fun `Skal gi true naar virusscan returnerer OK`() {
		// Gitt
		val fil = "testfil".toByteArray()

		// Når
		val result = antivirusService.scan(fil)

		// Så
		assertEquals(true, result)
	}

	@Test
	fun `Skal gi false naar virusscan returnerer FOUND`() {
		// Gitt
		val fil = "testfil".toByteArray()
		WireMock.setScenarioState("antivirus", "virus-found")

		// Når
		val result = antivirusService.scan(fil)

		// Så
		assertEquals(false, result)
	}

	@Test
	fun `Skal gi false naar virusscan returnerer ERROR`() {
		// Gitt
		val fil = "testfil".toByteArray()
		WireMock.setScenarioState("antivirus", "virus-error")

		// Når
		val result = antivirusService.scan(fil)

		// Så
		assertEquals(false, result)
	}
}
