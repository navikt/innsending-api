package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.imageFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.officeFileTypes
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.supportedFileTypes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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

	@ParameterizedTest
	@ValueSource(strings = ["bilde.jpg", "bilde.jpeg", "bilde.png", "bilde.bmp", "bilde.tif", "bilde.tiff", "bilde.gif", "office.docx", "office.doc", "office.odt", "office.rtf"])
	fun `supported image and office files are accepted as a type that can be converted`(filnavn: String) {
		val file = Hjelpemetoder.getBytesFromFile("/__files/$filnavn")

		val filtype = validerer.validereFilformat(innsendingId = "123456789", file = file, filnavn)

		assertTrue(filtype in imageFileTypes.keys + officeFileTypes.keys, "Unexpected type $filtype for $filnavn")
	}
}
