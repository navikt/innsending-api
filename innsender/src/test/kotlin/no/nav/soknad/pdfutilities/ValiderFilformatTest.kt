package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.supportedFileTypes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ValiderFilformatTest {

	val validerer = Validerer()

	@Test
	fun verifiserPdfFil() {
		val file = Hjelpemetoder.getBytesFromFile("/NAV 54-editert.pdf")
		assertDoesNotThrow {
			validerer.validereFilformat(innsendingId = "123456789", file = file, "NAV 54-editert.pdf")
		}
	}

	@Test
	fun verifiserImageFil() {
		val file = Hjelpemetoder.getBytesFromFile("/2MbJpg.jpg")
		assertDoesNotThrow {
			validerer.validereFilformat(innsendingId = "123456789", file = file, "2MbJpg.jpg")
		}
	}

	@Test
	@Disabled("Gammel JUnit4 som feiler")
	fun verifiserJsonFilGirFeilmelding() {
		val file = Hjelpemetoder.getBytesFromFile("/sanity.json")
		val exception = assertThrows<IllegalActionException> {
			validerer.validereFilformat(innsendingId = "123456789", file = file, "sanity.json")
		}
		assertTrue(exception.message.contains(" Ugyldig filtype for opplasting. Kan kun laste opp filer av type ${supportedFileTypes.joinToString(", ")}"))
	}

	@Test
	fun verifiserDocxFilIkkeGirFeilmelding() {
		val file = Hjelpemetoder.getBytesFromFile("/Docx-test.docx")
		assertDoesNotThrow {
			validerer.validereFilformat(innsendingId = "123456789", file = file, "Docx-test.docx")
		}
	}

	@Test
	fun verifiserTxtFilIkkeGirFeilmelding() {
		val file = Hjelpemetoder.getBytesFromFile("/__files/tekstfil-ex.txt")
		assertDoesNotThrow {
			validerer.validereFilformat(innsendingId = "123456789", file = file, "textfil-ex.txt")
		}
	}

		@Test
		fun verifiserXSLXFilGirFeilmelding() {
			val file = Hjelpemetoder.getBytesFromFile("/__files/filopplastingstyper.xlsx")
			val exception = assertThrows<IllegalActionException> {
				validerer.validereFilformat(innsendingId = "123456789", file = file, "filopplastingstyper.xlsx")
			}
			assertTrue(exception.message.contains(" Ugyldig filtype for opplasting. Kan kun laste opp filer av type ${supportedFileTypes.joinToString(", ")}"))
		}


	@Test
	fun verifiserJsonFilUtenExtentionGirFeilmelding() {
		val file = Hjelpemetoder.getBytesFromFile("/sanity.json")
		val exception = assertThrows<IllegalActionException> {
			validerer.validereFilformat(innsendingId = "123456789", file = file, "sanity")
		}
		assertTrue(exception.message.contains(" Ugyldig filtype for opplasting. Kan kun laste opp filer av type ${supportedFileTypes.joinToString(", ")}"))
	}

}
