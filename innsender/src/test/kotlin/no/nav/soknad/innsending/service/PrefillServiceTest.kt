package no.nav.soknad.innsending.service

import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.ApplicationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PrefillServiceTest : ApplicationTest() {

	@Autowired
	lateinit var prefillService: PrefillService

	@Test
	fun `Should get prefill data`() {
		// Given
		val properties = listOf("sokerFornavn", "sokerEtternavn")
		val userId = "12128012345"

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("Ola", result.sokerFornavn)
		assertEquals("Nordmann", result.sokerEtternavn)
	}


}
