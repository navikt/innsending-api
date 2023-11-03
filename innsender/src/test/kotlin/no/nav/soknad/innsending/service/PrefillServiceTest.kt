package no.nav.soknad.innsending.service

import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.ApplicationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PrefillServiceTest : ApplicationTest() {

	@Autowired
	lateinit var prefillService: PrefillService

	@Test
	fun `Should create correct service properties map based on input properties`() {
		// Given
		val inputList = listOf("sokerFornavn", "maalgruppe")
		val serviceKeyMap = mapOf("PDL" to listOf("sokerFornavn", "sokerEtternavn"), "ARENA" to listOf("maalgruppe"))

		// When
		val result = prefillService.createServicePropertiesMap(inputList, serviceKeyMap)

		// Then
		assertEquals(2, result.size)
		assertTrue(result.containsKey("PDL"))
		assertTrue(result.containsKey("ARENA"))
		assertEquals(1, result["PDL"]?.size)
		assertEquals(1, result["ARENA"]?.size)
		assertEquals("sokerFornavn", result["PDL"]?.get(0))
		assertEquals("maalgruppe", result["ARENA"]?.get(0))
	}

	@Test
	fun `Should find key in map for input string`() {
		// Given
		val inputString = "sokerFornavn"
		val inputMap = mapOf("PDL" to listOf("sokerFornavn", "sokerEtternavn"), "ARENA" to listOf("maalgruppe"))

		// When
		val result = prefillService.findKeyForString(inputString, inputMap)

		// Then
		assertEquals("PDL", result)
	}

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
