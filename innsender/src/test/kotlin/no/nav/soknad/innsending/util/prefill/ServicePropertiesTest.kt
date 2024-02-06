package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.util.Constants.ARENA_MAALGRUPPE
import no.nav.soknad.innsending.util.Constants.PDL
import no.nav.soknad.innsending.util.prefill.ServiceProperties.createServicePropertiesMap
import no.nav.soknad.innsending.util.prefill.ServiceProperties.findKeyForString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServicePropertiesTest {
	@Test
	fun `Should create correct service properties map based on input properties`() {
		// Given
		val inputList = listOf("sokerFornavn", "sokerMaalgruppe")

		// When
		val result = createServicePropertiesMap(inputList)

		// Then
		assertEquals(2, result.size)
		assertTrue(result.containsKey(PDL))
		assertTrue(result.containsKey(ARENA_MAALGRUPPE))
		assertEquals(1, result[PDL]?.size)
		assertEquals(1, result[ARENA_MAALGRUPPE]?.size)
		assertEquals("sokerFornavn", result[PDL]?.get(0))
		assertEquals("sokerMaalgruppe", result[ARENA_MAALGRUPPE]?.get(0))
	}

	@Test
	fun `Should find key in map for input string`() {
		// Given
		val inputString = "sokerFornavn"
		val inputMap =
			mapOf(PDL to listOf("sokerFornavn", "sokerEtternavn"), ARENA_MAALGRUPPE to listOf("sokerMaalgruppe"))

		// When
		val result = findKeyForString(inputString, inputMap)

		// Then
		assertEquals(PDL, result)
	}
}
