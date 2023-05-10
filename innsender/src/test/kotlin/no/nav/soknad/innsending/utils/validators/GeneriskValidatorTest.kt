package no.nav.soknad.innsending.utils.validators

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.util.validators.validerLikeFelter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GeneriskValidatorTest {

	data class Person(val navn: String, val alder: Int)

	@Test
	fun `Skal kaste exception når to felter ikke er like`() {

		// Gitt
		val person1 = Person("John", 30)
		val person2 = Person("John", 31)

		// Så
		val exception = assertThrows<IllegalActionException> {
			validerLikeFelter(
				person1,
				person2,
				listOf(Person::navn, Person::alder)
			)
		}

		assertEquals("Felter er ikke like for Person: alder", exception.message)

	}

	@Test
	fun `Skal ikke kaste exception når alle de gitte feltene er like`() {
		// Gitt
		// Gitt
		val person1 = Person("John", 30)
		val person2 = Person("John", 30)

		// Så
		assertDoesNotThrow {
			validerLikeFelter(
				person1,
				person2,
				listOf(
					Person::navn,
					Person::alder,
				)
			)
		}
	}

}
