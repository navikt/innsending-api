package no.nav.soknad.pdfutilities

import junit.framework.TestCase.assertTrue
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.pdfutilities.gotenberg.ToPdfConverterTest
import org.junit.Test
import kotlin.test.assertEquals

class KonverterTilPdfTest {

	private val docToPdfConverter: DocxToPdfInterface = ToPdfConverterTest()

	private val konverterTilPdf = KonverterTilPdf(docToPdfConverter)

	private val soknadDto = DokumentSoknadDtoTestBuilder().build()


	@Test
	fun verifiserFlatingAvPdf() {
		val skrivbarPdf = Hjelpemetoder.getBytesFromFile("/NAV 54-editert.pdf")
		assertTrue(konverterTilPdf.harSkrivbareFelt(skrivbarPdf))

		val antallSiderSkrivbarPdf = AntallSider().finnAntallSider(skrivbarPdf)
		val start = System.currentTimeMillis()
		val flatetPdf = konverterTilPdf.flatUtPdf(skrivbarPdf, antallSiderSkrivbarPdf ?: 0)
		val ferdig = System.currentTimeMillis()
		println("Tid til flate ut PDF = ${ferdig - start}")

		//writeBytesToFile(flatetPdf, "./delme.pdf")
		assertEquals(false, konverterTilPdf.harSkrivbareFelt(flatetPdf))

		val antallSiderFlatetPdf = AntallSider().finnAntallSider(flatetPdf)
		assertEquals(antallSiderSkrivbarPdf, antallSiderFlatetPdf)

		val erPdfa = Validerer().isPDFa(flatetPdf)
		assertTrue(erPdfa)

	}

	@Test
	fun `Skal ikke flate ut PDF'er på over 50 sider`() {
		val skrivbarPdf = Hjelpemetoder.getBytesFromFile("/pdfs/acroform-fields-tom-array.pdf")
		assertTrue(konverterTilPdf.harSkrivbareFelt(skrivbarPdf))

		val antallSiderSkrivbarPdf = AntallSider().finnAntallSider(skrivbarPdf) ?: 0
		val flatetPdf = konverterTilPdf.flatUtPdf(skrivbarPdf, antallSiderSkrivbarPdf)
		assertTrue(konverterTilPdf.harSkrivbareFelt(flatetPdf)) // Skal fortsatt ha skrivbare felt

		assertEquals(antallSiderSkrivbarPdf, AntallSider().finnAntallSider(flatetPdf))
		assertEquals(skrivbarPdf, flatetPdf)
	}

	@Test
	fun verifiserKonverteringAvJpg() {
		val jpg = Hjelpemetoder.getBytesFromFile("/2MbJpg.jpg")

		val (pdf, antallSider) = konverterTilPdf.tilPdf(jpg, soknadDto)
		assertEquals(1, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		assertTrue(erPdfa)
	}

	@Test
	fun verifiserKonverteringAvMellomstorJpg() {
		val jpg = Hjelpemetoder.getBytesFromFile("/mellomstorJpg.jpg")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(jpg, soknadDto)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av mellomstorJpg = ${ferdig - start}")
		assertEquals(1, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		assertTrue(erPdfa)
	}


	@Test
	fun verifiserKonverteringAvTxtFil() {
		val jpg = Hjelpemetoder.getBytesFromFile("/__files/test-ex2.txt")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(
			jpg,
			soknadDto,
			sammensattNavn = "Kalle Kanin",
			vedleggsTittel = "Vedleggstittel"
		)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av txtFil = ${ferdig - start}")
		assertEquals(11, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		assertTrue(erPdfa)
	}



	@Test
	fun verifiserKonverteringAvNotepadEncodedTxtFil() {
		val jpg = Hjelpemetoder.getBytesFromFile("/__files/tekst-notepad-encoding.txt")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = konverterTilPdf.tilPdf(
			jpg,
			soknadDto,
			sammensattNavn = "Kalle Kanin",
			vedleggsTittel = "Vedleggstittel"
		)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av txtFil = ${ferdig - start}")
		assertEquals(2, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		assertTrue(erPdfa)
	}

}
