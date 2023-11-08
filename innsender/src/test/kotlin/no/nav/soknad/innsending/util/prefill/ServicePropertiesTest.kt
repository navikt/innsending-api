package no.nav.soknad.innsending.util.prefill

import no.nav.soknad.innsending.util.Constants.ARENA_MAALGRUPPER
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
		val inputList = listOf("sokerFornavn", "sokerMaalgrupper")

		// When
		val result = createServicePropertiesMap(inputList)

		// Then
		assertEquals(2, result.size)
		assertTrue(result.containsKey(PDL))
		assertTrue(result.containsKey(ARENA_MAALGRUPPER))
		assertEquals(1, result[PDL]?.size)
		assertEquals(1, result[ARENA_MAALGRUPPER]?.size)
		assertEquals("sokerFornavn", result[PDL]?.get(0))
		assertEquals("sokerMaalgrupper", result[ARENA_MAALGRUPPER]?.get(0))
	}

	@Test
	fun `Should find key in map for input string`() {
		// Given
		val inputString = "sokerFornavn"
		val inputMap =
			mapOf(PDL to listOf("sokerFornavn", "sokerEtternavn"), ARENA_MAALGRUPPER to listOf("sokerMaalgrupper"))

		// When
		val result = findKeyForString(inputString, inputMap)

		// Then
		assertEquals(PDL, result)
	}
}
