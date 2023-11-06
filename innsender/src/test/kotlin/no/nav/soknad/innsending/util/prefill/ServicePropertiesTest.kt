package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.util.prefill.ServiceProperties.createServicePropertiesMap
import no.nav.soknad.innsending.util.prefill.ServiceProperties.findKeyForString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServicePropertiesTest {
	@Test
	fun `Should create correct service properties map based on input properties`() {
		// Given
		val inputList = listOf("sokerFornavn", "sokerMaalgruppetype")
		val serviceKeyMap =
			mapOf("PDL" to listOf("sokerFornavn", "sokerEtternavn"), "ARENA" to listOf("sokerMaalgruppetype"))

		// When
		val result = createServicePropertiesMap(inputList, serviceKeyMap)

		// Then
		assertEquals(2, result.size)
		assertTrue(result.containsKey("PDL"))
		assertTrue(result.containsKey("ARENA"))
		assertEquals(1, result["PDL"]?.size)
		assertEquals(1, result["ARENA"]?.size)
		assertEquals("sokerFornavn", result["PDL"]?.get(0))
		assertEquals("sokerMaalgruppetype", result["ARENA"]?.get(0))
	}

	@Test
	fun `Should find key in map for input string`() {
		// Given
		val inputString = "sokerFornavn"
		val inputMap = mapOf("PDL" to listOf("sokerFornavn", "sokerEtternavn"), "ARENA" to listOf("sokerMaalgruppetype"))

		// When
		val result = findKeyForString(inputString, inputMap)

		// Then
		assertEquals("PDL", result)
	}
}
