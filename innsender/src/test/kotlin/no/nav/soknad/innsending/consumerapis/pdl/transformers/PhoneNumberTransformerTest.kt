package no.nav.soknad.innsending.consumerapis.pdl.transformers

import no.nav.soknad.innsending.pdl.generated.prefilldata.Telefonnummer
import no.nav.soknad.innsending.utils.builders.pdl.TelefonnummerTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

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

	@Test
	fun `Should return concatenated landskode and nummer with lowest priority and with trimmed string`() {
		// Given
		val phoneNumbers = listOf(
			TelefonnummerTestBuilder().landskode("+47").nummer("234567890").prioritet(6).build(),
			TelefonnummerTestBuilder().landskode("+47").nummer("123 456 78").prioritet(3).build(),
			TelefonnummerTestBuilder().landskode("+46").nummer("98765432").prioritet(4).build()
		)

		// When
		val result = PhoneNumberTransformer.transformPhoneNumbers(phoneNumbers)

		// Then
		assertEquals("+4712345678", result)
	}

	@Test
	fun `Should return null if phonenumber is empty list`() {
		// Given
		val phoneNumbers = emptyList<Telefonnummer>()

		// When
		val result = PhoneNumberTransformer.transformPhoneNumbers(phoneNumbers)

		// Then
		assertNull(result)
	}
}
