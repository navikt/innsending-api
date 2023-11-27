package no.nav.soknad.innsending.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkjemaTest {

	@Test
	fun `Should lowercase, trim and remove special characters`() {
		val skjemanr = "NAV 10-07.08"
		assertEquals("nav100708", Skjema.createSkjemaPathFromSkjemanr(skjemanr))
	}

	@Test
	fun `Should remove all non-alphanumeric characters from skjemanr`() {
		val inputWithSpecialChars = "A!@#B*C123"
		assertEquals("ABC123", Skjema.createSkjemaPathFromSkjemanr(inputWithSpecialChars))

	}


}
