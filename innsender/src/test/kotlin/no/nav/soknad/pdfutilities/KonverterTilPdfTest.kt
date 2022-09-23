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

		val flatetPdf = KonverterTilPdf().flatUtPdf(skrivbarPdf)
		writeBytesToFile(flatetPdf, "./delme.pdf")
		assertEquals(false, KonverterTilPdf().harSkrivbareFelt(flatetPdf))

		val antallSider = AntallSider().finnAntallSider(skrivbarPdf)
		assertEquals(antallSider, AntallSider().finnAntallSider(flatetPdf))

	}

}
