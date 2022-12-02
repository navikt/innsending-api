package no.nav.soknad.pdfutilities

import junit.framework.TestCase.assertTrue
import no.nav.soknad.innsending.utils.getBytesFromFile
import no.nav.soknad.innsending.utils.writeBytesToFile
import org.junit.Test
import kotlin.test.assertEquals

class KonverterTilPdfTest {

	@Test
	fun verifiserFlatingAvPdf() {
		val skrivbarPdf = getBytesFromFile("/NAV 54-editert.pdf")
		assertTrue(KonverterTilPdf().harSkrivbareFelt(skrivbarPdf))

		val start = System.currentTimeMillis()
		val flatetPdf = KonverterTilPdf().flatUtPdf(skrivbarPdf)
		val ferdig = System.currentTimeMillis()
		System.out.println("Tid til flate ut PDF = ${ferdig-start}")
		//writeBytesToFile(flatetPdf, "./delme.pdf")
		assertEquals(false, KonverterTilPdf().harSkrivbareFelt(flatetPdf))

		val antallSider = AntallSider().finnAntallSider(skrivbarPdf)
		assertEquals(antallSider, AntallSider().finnAntallSider(flatetPdf))

		val erPdfa = Validerer().isPDFa(flatetPdf)
		assertTrue(erPdfa)

	}

	@Test
	fun verifiserKonverteringAvJpg() {
		val jpg = getBytesFromFile("/2MbJpg.jpg")

		val pdf = KonverterTilPdf().tilPdf(jpg)
		val antallSider = AntallSider().finnAntallSider(pdf)
		assertEquals(1, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		assertTrue(erPdfa)
	}

	@Test
	fun verifiserKonverteringAvMellomstorJpg() {
		val jpg = getBytesFromFile("/mellomstorJpg.jpg")

		val start = System.currentTimeMillis()
		val pdf = KonverterTilPdf().tilPdf(jpg)
		val ferdig = System.currentTimeMillis()
		System.out.println("Tid til konvertering av mellomstorJpg = ${ferdig-start}")
		val antallSider = AntallSider().finnAntallSider(pdf)
		assertEquals(1, antallSider)

		val erPdfa = Validerer().isPDFa(pdf)
		assertTrue(erPdfa)
	}

}
