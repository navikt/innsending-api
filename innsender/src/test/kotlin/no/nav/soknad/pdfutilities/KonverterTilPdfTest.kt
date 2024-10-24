package no.nav.soknad.pdfutilities

import junit.framework.TestCase.assertTrue
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.Test
import kotlin.test.assertEquals

class KonverterTilPdfTest {

	@Test
	fun verifiserFlatingAvPdf() {
		val skrivbarPdf = Hjelpemetoder.getBytesFromFile("/NAV 54-editert.pdf")
		assertTrue(KonverterTilPdf().harSkrivbareFelt(skrivbarPdf))

		val antallSiderSkrivbarPdf = AntallSider().finnAntallSider(skrivbarPdf)
		val start = System.currentTimeMillis()
		val flatetPdf = KonverterTilPdf().flatUtPdf(skrivbarPdf, antallSiderSkrivbarPdf ?: 0)
		val ferdig = System.currentTimeMillis()
		println("Tid til flate ut PDF = ${ferdig - start}")

		//writeBytesToFile(flatetPdf, "./delme.pdf")
		assertEquals(false, KonverterTilPdf().harSkrivbareFelt(flatetPdf))

		val antallSiderFlatetPdf = AntallSider().finnAntallSider(flatetPdf)
		assertEquals(antallSiderSkrivbarPdf, antallSiderFlatetPdf)

		val erPdfa = Validerer().isPDFa(flatetPdf)
		assertTrue(erPdfa)

	}

	@Test
	fun `Skal ikke flate ut PDF'er på over 50 sider`() {
		val skrivbarPdf = Hjelpemetoder.getBytesFromFile("/pdfs/acroform-fields-tom-array.pdf")
		assertTrue(KonverterTilPdf().harSkrivbareFelt(skrivbarPdf))

		val antallSiderSkrivbarPdf = AntallSider().finnAntallSider(skrivbarPdf) ?: 0
		val flatetPdf = KonverterTilPdf().flatUtPdf(skrivbarPdf, antallSiderSkrivbarPdf)
		assertTrue(KonverterTilPdf().harSkrivbareFelt(flatetPdf)) // Skal fortsatt ha skrivbare felt

		assertEquals(antallSiderSkrivbarPdf, AntallSider().finnAntallSider(flatetPdf))
		assertEquals(skrivbarPdf, flatetPdf)
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
