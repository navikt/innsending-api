package no.nav.soknad.innsending.consumerapis.pdl.transformers

import no.nav.soknad.innsending.utils.builders.pdl.TelefonnummerTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhoneNumberTransformerTest {
	@Test
	fun `Should return concatenated landskode and nummer`() {
		// Given
		val phoneNumbers = listOf(
			TelefonnummerTestBuilder().landskode("+47").nummer("12345678").prioritet(1).build(),
			TelefonnummerTestBuilder().landskode("+46").nummer("98765432").prioritet(2).build()
		)

		// When
		val result = PhoneNumberTransformer.transformPhoneNumbers(phoneNumbers)

		// Then
		assertEquals("+4712345678", result)
	}
}
