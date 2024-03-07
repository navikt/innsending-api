package no.nav.soknad.pdfutilities

import junit.framework.TestCase.assertTrue
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
import org.junit.Test
import kotlin.test.assertEquals

class KonverterTilPdfTest {

	@Test
	fun verifiserFlatingAvPdf() {
		val skrivbarPdf = Hjelpemetoder.getBytesFromFile("/pdfs/writable-overlapping.pdf")
		assertTrue(KonverterTilPdf().harSkrivbareFelt(skrivbarPdf))

		val antallSiderSkrivbarPdf = AntallSider().finnAntallSider(skrivbarPdf)
		val flatetPdf = KonverterTilPdf().flatUtPdf(skrivbarPdf, antallSiderSkrivbarPdf ?: 0)

		writeBytesToFile(flatetPdf, "./testing.pdf")

		val antallSiderFlatetPdf = AntallSider().finnAntallSider(flatetPdf)
		assertEquals(antallSiderSkrivbarPdf, antallSiderFlatetPdf)
	}

	@Test
	fun `Should not have overlapping text when PDF has been updated and saved multiple times (history of text changes overlaps)`() {
		val skrivbarPdf = Hjelpemetoder.getBytesFromFile("/pdfs/writable-overlapping.pdf")
		assertTrue(KonverterTilPdf().harSkrivbareFelt(skrivbarPdf))

		val antallSiderSkrivbarPdf = AntallSider().finnAntallSider(skrivbarPdf)
		val flatetPdf = KonverterTilPdf().flatUtPdf(skrivbarPdf, antallSiderSkrivbarPdf ?: 0)

		writeBytesToFile(flatetPdf, "./testing.pdf")

		val antallSiderFlatetPdf = AntallSider().finnAntallSider(flatetPdf)
		assertEquals(antallSiderSkrivbarPdf, antallSiderFlatetPdf)
	}

	@Test
	fun verifiserKonverteringAvJpg() {
		val jpg = Hjelpemetoder.getBytesFromFile("/2MbJpg.jpg")

		val (pdf, antallSider) = KonverterTilPdf().tilPdf(jpg)
		assertEquals(1, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		assertTrue(erPdfa)
	}

	@Test
	fun verifiserKonverteringAvMellomstorJpg() {
		val jpg = Hjelpemetoder.getBytesFromFile("/mellomstorJpg.jpg")

		val start = System.currentTimeMillis()
		val (pdf, antallSider) = KonverterTilPdf().tilPdf(jpg)
		val ferdig = System.currentTimeMillis()
		println("Tid til konvertering av mellomstorJpg = ${ferdig - start}")
		assertEquals(1, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		assertTrue(erPdfa)
	}

}
