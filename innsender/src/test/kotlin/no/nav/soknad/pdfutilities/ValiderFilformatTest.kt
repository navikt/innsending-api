package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ValiderFilformatTest {

	@Test
	fun verifiserPdfFil() {
		val file = Hjelpemetoder.getBytesFromFile("/NAV 54-editert.pdf")
		assertDoesNotThrow {
			Validerer().validereFilformat(innsendingId = "123456789", file = file, "NAV 54-editert.pdf")
		}
	}

	@Test
	fun verifiserImageFil() {
		val file = Hjelpemetoder.getBytesFromFile("/2MbJpg.jpg")
		assertDoesNotThrow {
			Validerer().validereFilformat(innsendingId = "123456789", file = file, "2MbJpg.jpg")
		}
	}

	@Test
	fun verifiserJsonFilGirFeilmelding() {
		val file = Hjelpemetoder.getBytesFromFile("/sanity.json")
		val exception = assertThrows<IllegalActionException> {
			Validerer().validereFilformat(innsendingId = "123456789", file = file, "sanity.json")
		}
		assertTrue(exception.message.contains(" Ugyldig filtype for opplasting. Kan kun laste opp filer av type PDF, JPEG, PNG og IMG"))
	}

	@Test
	fun verifiserDocxFilIkkeGirFeilmelding() {
		val file = Hjelpemetoder.getBytesFromFile("/Docx test dokument.docx")
		assertDoesNotThrow {
			Validerer().validereFilformat(innsendingId = "123456789", file = file, "Docx test dokument.docx")
		}
	}

	@Test
	fun verifiserTxtFilIkkeGirFeilmelding() {
		val file = Hjelpemetoder.getBytesFromFile("/__files/tekstfil-ex.txt")
		assertDoesNotThrow {
			Validerer().validereFilformat(innsendingId = "123456789", file = file, "textfil-ex.txt")
		}
	}

	/*
		@Test
		fun verifiserXSLXFilGirFeilmelding() {
			val file = Hjelpemetoder.getBytesFromFile("/__files/filopplastingstyper.xlsx")
			val exception = assertThrows<IllegalActionException> {
				Validerer().validereFilformat(innsendingId = "123456789", file = file, "sanity.json")
			}
			assertTrue(exception.message.contains(" Ugyldig filtype for opplasting. Kan kun laste opp filer av type PDF, JPEG, PNG og IMG"))
		}
	*/


	@Test
	fun verifiserJsonFilUtenExtentionGirFeilmelding() {
		val file = Hjelpemetoder.getBytesFromFile("/sanity.json")
		val exception = assertThrows<IllegalActionException> {
			Validerer().validereFilformat(innsendingId = "123456789", file = file, "sanity")
		}
		assertTrue(exception.message.contains(" Ugyldig filtype for opplasting. Kan kun laste opp filer av type PDF, JPEG, PNG og IMG"))
	}

}
