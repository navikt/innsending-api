package no.nav.soknad.innsending.consumerapis.antivirus

import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.soknad.innsending.ApplicationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class AntivirusServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var antivirusService: AntivirusService

	@Test
	fun `Skal gi OK når fil ikke har virus`() {
		// Gitt
		val fil = "testfil".toByteArray()

		// Når
		val result = antivirusService.scan(fil)

		// Så
		assertEquals(true, result)
	}

	@Test
	fun `Skal gi FOUND når fil har virus`() {
		// Gitt
		val fil = "testfil".toByteArray()
		WireMock.setScenarioState("antivirus", "virus-found")

		// Når
		val result = antivirusService.scan(fil)

		// Så
		assertEquals(false, result)
	}
}
