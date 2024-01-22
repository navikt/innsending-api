package no.nav.soknad.innsending.consumerapis.kontoregister

import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.NonCriticalException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class KontoregisterServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var kontoregisterInterface: KontoregisterInterface

	val expectedKontonummer = "8361347234732292"

	@Test
	fun `Should get correct kontonummer`() {

		// When
		val kontonummer = kontoregisterInterface.getKontonummer()

		// Then
		assertEquals(expectedKontonummer, kontonummer)
	}

	@Test
	fun `Should return NonCriticalException on failure`() {
		// Given
		WireMock.setScenarioState("kontoregister-borger", "failed")

		// When / Then
		assertThrows<NonCriticalException> {
			kontoregisterInterface.getKontonummer()
		}
	}


}
