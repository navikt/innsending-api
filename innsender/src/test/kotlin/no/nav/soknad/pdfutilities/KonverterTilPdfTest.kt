package no.nav.soknad.pdfutilities

import junit.framework.TestCase.assertTrue
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class KonverterTilPdfTest: ApplicationTest() {

	@Autowired
	private lateinit var konverterTilPdf: KonverterTilPdf

	private val soknadDto = DokumentSoknadDtoTestBuilder().build()

	@Test
	fun verifiserKonverteringAvJpg() {
		val jpg = Hjelpemetoder.getBytesFromFile("/2MbJpg.jpg")

		val (pdf, antallSider) = konverterTilPdf.tilPdf(jpg, soknadDto, ".jpg")
		assertEquals(1, antallSider)

		val validation = VeraPDFValidator().validatePdf(pdf)

		assertTrue(validation.isPdfACompliant)
	}

	@Test
	fun verifiserKonverteringAvMellomstorJpg() {
		val jpg = Hjelpemetoder.getBytesFromFile("/mellomstorJpg.jpg")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(jpg, soknadDto, ".jpg")
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av mellomstorJpg = ${ferdig - start}")
		assertEquals(1, antallSider)

		val validation = VeraPDFValidator().validatePdf(pdf)

		assertTrue(validation.isPdfACompliant)
	}


	@Test
	fun verifiserKonverteringAvTxtFil() {
		val jpg = Hjelpemetoder.getBytesFromFile("/__files/test-ex2.txt")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(
			jpg,
			soknadDto,
			filtype = ".txt",
			vedleggsTittel = "Vedleggstittel"
		)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av txtFil = ${ferdig - start}")
		assertEquals(11, antallSider)

		val validation = VeraPDFValidator().validatePdf(pdf)

		assertTrue(validation.isPdfACompliant)
	}



	@Test
	fun verifiserKonverteringAvNotepadEncodedTxtFil() {
		val jpg = Hjelpemetoder.getBytesFromFile("/__files/tekst-notepad-encoding.txt")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(
			jpg,
			soknadDto,
			filtype = ".txt",
			vedleggsTittel = "Vedleggstittel"
		)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av txtFil = ${ferdig - start}")
		assertEquals(2, antallSider)

		val validation = VeraPDFValidator().validatePdf(pdf)

		assertTrue(validation.isPdfACompliant)
	}

}
