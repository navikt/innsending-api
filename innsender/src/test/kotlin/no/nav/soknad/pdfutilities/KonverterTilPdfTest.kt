package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
import org.junit.jupiter.api.Assertions.assertTrue

class KonverterTilPdfTest: ApplicationTest() {

	@Autowired
	private lateinit var konverterTilPdf: KonverterTilPdf

	private val soknadDto = DokumentSoknadDtoTestBuilder().build()

	@Test
	fun verifiserKonverteringAvJpg() {
		val jpg = Hjelpemetoder.getBytesFromFile("/2MbJpg.jpg")

		val (pdf, antallSider) = konverterTilPdf.tilPdf(jpg, soknadDto.innsendingsId!!, ".jpg", "Annet", "nb-NO")
		assertEquals(1, antallSider)

		val validation = VeraPDFValidator().validatePdf(pdf)

		assertTrue(validation.isPdfACompliant)
	}

	@Test
	fun verifiserKonverteringAvMellomstorJpg() {
		val jpg = Hjelpemetoder.getBytesFromFile("/mellomstorJpg.jpg")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(jpg, soknadDto.innsendingsId!!, ".jpg", "Annet", "nb-NO")
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av mellomstorJpg = ${ferdig - start}")
		assertEquals(1, antallSider)

		val validation = VeraPDFValidator().validatePdf(pdf)

		assertTrue(validation.isPdfACompliant)
	}


	@Test
	fun verifiserKonverteringPdfTilBildeOgTilPdf() {
		val orig = Hjelpemetoder.getBytesFromFile("/__files/forside-nav.pdf")

		val start = System.currentTimeMillis()
		val png = KonverterTilPng().konverterTilPng(orig)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av forside-nav = ${ferdig - start}")
		writeBytesToFile(png[0], "../target/forside-nav.png")
0	}

	@Test
	fun verifiserKonverteringAvTxtFil() {
		val jpg = Hjelpemetoder.getBytesFromFile("/__files/test-ex2.txt")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(
			jpg,
			soknadDto.innsendingsId!!,
			filtype = ".txt",
			tittel = "Vedleggstittel",
			spraak = "nb-NO",
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
			soknadDto.innsendingsId!!,
			filtype = ".txt",
			tittel = "Vedleggstittel",
			spraak = "nb-NO",
		)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av txtFil = ${ferdig - start}")
		assertEquals(2, antallSider)

		val validation = VeraPDFValidator().validatePdf(pdf)

		assertTrue(validation.isPdfACompliant)
	}


	@Test
	fun verifiserKonverteringAvDocxFil() {
		val doc = Hjelpemetoder.getBytesFromFile("/__files/soknadsarkiverer-og-flere-poder.docx")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(
			doc,
			soknadDto.innsendingsId!!,
			filtype = ".docx",
			tittel = "Vedleggstittel",
			spraak = "nb-NO"
		)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av txtFil = ${ferdig - start}")
		assertEquals(13, antallSider)

		val validation = VeraPDFValidator().validatePdf(pdf)

		assertTrue(validation.isPdfACompliant)
	}



}
